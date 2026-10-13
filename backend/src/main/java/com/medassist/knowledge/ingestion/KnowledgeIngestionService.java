package com.medassist.knowledge.ingestion;

import com.medassist.audit.application.AuditEntry;
import com.medassist.audit.application.AuditService;
import com.medassist.audit.domain.AuditEventType;
import com.medassist.common.error.DomainRuleViolationException;
import com.medassist.common.json.JsonCodec;
import com.medassist.knowledge.chunking.ChunkingService;
import com.medassist.knowledge.chunking.KnowledgeChunk;
import com.medassist.knowledge.chunking.KnowledgeChunkRepository;
import com.medassist.knowledge.chunking.SectionDetector;
import com.medassist.knowledge.chunking.TextBlock;
import com.medassist.knowledge.chunking.TextChunk;
import com.medassist.knowledge.chunking.TextCleaner;
import com.medassist.knowledge.document.DocumentFormat;
import com.medassist.knowledge.document.DocumentParser;
import com.medassist.knowledge.document.ExtractedDocument;
import com.medassist.knowledge.document.IngestionStatus;
import com.medassist.knowledge.document.KnowledgeDocument;
import com.medassist.knowledge.document.KnowledgeDocumentRepository;
import com.medassist.knowledge.document.KnowledgeDocumentSourceRepository;
import com.medassist.knowledge.embedding.EmbeddingService;
import com.medassist.knowledge.retrieval.VectorRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Ingestion pipeline: text extraction -> cleanup -> section detection -> chunking -> metadata enrichment ->
 * embeddings -> pgvector. Each stage is persisted so the admin UI shows real ingestion status.
 */
@Service
public class KnowledgeIngestionService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestionService.class);
    static final int MAX_EXTRACTED_CHARS = 3_000_000;

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeDocumentSourceRepository sourceRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final DocumentParser parser;
    private final TextCleaner cleaner;
    private final SectionDetector sectionDetector;
    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final VectorRepository vectorRepository;
    private final AuditService auditService;
    private final TransactionTemplate tx;
    private final JsonCodec json;
    private final Clock clock;
    private final Timer timer;

    public KnowledgeIngestionService(KnowledgeDocumentRepository documentRepository,
                                     KnowledgeDocumentSourceRepository sourceRepository,
                                     KnowledgeChunkRepository chunkRepository, DocumentParser parser,
                                     TextCleaner cleaner, SectionDetector sectionDetector,
                                     ChunkingService chunkingService, EmbeddingService embeddingService,
                                     VectorRepository vectorRepository, AuditService auditService,
                                     TransactionTemplate tx, JsonCodec json, Clock clock, MeterRegistry meterRegistry) {
        this.documentRepository = documentRepository;
        this.sourceRepository = sourceRepository;
        this.chunkRepository = chunkRepository;
        this.parser = parser;
        this.cleaner = cleaner;
        this.sectionDetector = sectionDetector;
        this.chunkingService = chunkingService;
        this.embeddingService = embeddingService;
        this.vectorRepository = vectorRepository;
        this.auditService = auditService;
        this.tx = tx;
        this.json = json;
        this.clock = clock;
        this.timer = Timer.builder("knowledge_ingestion_duration")
                .description("Duration of document extraction, chunking and embedding").register(meterRegistry);
    }

    public void ingest(UUID documentId, String actor) {
        long start = System.nanoTime();
        try {
            KnowledgeDocument document = stage(documentId, IngestionStatus.EXTRACTING);
            byte[] content = sourceRepository.findById(documentId)
                    .orElseThrow(() -> new IllegalStateException("Document source is missing")).getContent();
            DocumentFormat format = DocumentFormat.fromMediaType(document.getContentType());
            ExtractedDocument extracted = parser.parse(format, content);
            boolean markdown = format == DocumentFormat.MARKDOWN;
            List<SectionDetector.PageText> pages = extracted.pages().stream()
                    .map(p -> new SectionDetector.PageText(p.pageNumber(), cleaner.clean(p.text(), markdown)))
                    .toList();
            String cleanedText = String.join("\n\n", pages.stream().map(SectionDetector.PageText::text).toList());
            if (cleanedText.isBlank()) {
                throw new DomainRuleViolationException("No text could be extracted (scanned documents need OCR first)");
            }
            if (cleanedText.length() > MAX_EXTRACTED_CHARS) {
                throw new DomainRuleViolationException("Extracted text exceeds the supported size");
            }

            stage(documentId, IngestionStatus.CHUNKING);
            List<TextBlock> blocks = sectionDetector.detect(pages);
            List<TextChunk> chunks = chunkingService.chunk(blocks);
            Instant now = Instant.now(clock);
            List<KnowledgeChunk> entities = new ArrayList<>();
            for (TextChunk chunk : chunks) {
                entities.add(new KnowledgeChunk(documentId, chunk, json.write(metadata(document, chunk)), now));
            }
            tx.executeWithoutResult(s -> {
                chunkRepository.deleteByDocumentId(documentId);
                chunkRepository.flush();
                chunkRepository.saveAll(entities);
            });

            stage(documentId, IngestionStatus.EMBEDDING);
            List<float[]> vectors = embeddingService.embed(entities.stream().map(KnowledgeChunk::getContent).toList());
            tx.executeWithoutResult(s -> {
                for (int i = 0; i < entities.size(); i++) {
                    vectorRepository.store(entities.get(i).getId(), embeddingService.modelId(), vectors.get(i));
                }
            });

            tx.executeWithoutResult(s -> {
                KnowledgeDocument doc = documentRepository.findById(documentId).orElseThrow();
                doc.indexed(entities.size(), extracted.pageCount(), cleanedText, embeddingService.modelId(),
                        Instant.now(clock));
                supersedePreviousVersions(doc, actor);
                auditService.record(AuditEntry.of(AuditEventType.KNOWLEDGE_INGESTION_COMPLETED)
                        .actor(actor)
                        .entity("KnowledgeDocument", documentId)
                        .with("version", doc.getDocumentVersion())
                        .with("chunks", entities.size())
                        .with("embeddingModel", embeddingService.modelId()));
            });
        } catch (RuntimeException e) {
            String message = e instanceof DomainRuleViolationException ? e.getMessage()
                    : "Ingestion failed (" + e.getClass().getSimpleName() + "). See server logs.";
            log.warn("Knowledge ingestion failed for document {}", documentId, e);
            tx.executeWithoutResult(s -> documentRepository.findById(documentId).ifPresent(d -> d.failed(message)));
            auditService.recordIndependently(AuditEntry.of(AuditEventType.KNOWLEDGE_INGESTION_FAILED)
                    .actor(actor)
                    .entity("KnowledgeDocument", documentId)
                    .with("errorType", e.getClass().getSimpleName())
                    .failure());
        } finally {
            timer.record(Duration.ofNanos(System.nanoTime() - start));
        }
    }

    private void supersedePreviousVersions(KnowledgeDocument doc, String actor) {
        for (KnowledgeDocument other : documentRepository.findBySeriesIdOrderByUploadedAtDesc(doc.getSeriesId())) {
            if (!other.getId().equals(doc.getId()) && other.isEnabled() && other.getUploadedAt().isBefore(doc.getUploadedAt())) {
                other.supersededBy(doc.getId());
                auditService.record(AuditEntry.of(AuditEventType.KNOWLEDGE_DOCUMENT_UPDATED)
                        .actor(actor)
                        .entity("KnowledgeDocument", other.getId())
                        .with("change", "SUPERSEDED")
                        .with("supersededBy", doc.getId()));
            }
        }
    }

    private KnowledgeDocument stage(UUID documentId, IngestionStatus status) {
        return tx.execute(s -> {
            KnowledgeDocument doc = documentRepository.findById(documentId).orElseThrow();
            doc.ingestionStage(status);
            return doc;
        });
    }

    private static Map<String, Object> metadata(KnowledgeDocument d, TextChunk chunk) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("documentId", d.getId().toString());
        m.put("title", d.getTitle());
        m.put("section", chunk.section());
        m.put("page", chunk.pageNumber());
        m.put("source", d.getSourceOrganization());
        m.put("sourceUrl", d.getSourceUrl());
        m.put("documentVersion", d.getDocumentVersion());
        m.put("publicationDate", d.getPublicationDate() == null ? null : d.getPublicationDate().toString());
        m.put("jurisdiction", d.getJurisdiction());
        m.put("documentType", d.getDocumentType().name());
        m.put("chunkIndex", chunk.index());
        m.put("synthetic", d.isSynthetic());
        return m;
    }
}

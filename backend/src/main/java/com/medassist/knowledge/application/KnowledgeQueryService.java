package com.medassist.knowledge.application;

import com.medassist.audit.application.AuditEntry;
import com.medassist.audit.application.AuditService;
import com.medassist.audit.domain.AuditEventType;
import com.medassist.knowledge.chunking.ChunkingProperties;
import com.medassist.knowledge.chunking.KnowledgeChunk;
import com.medassist.knowledge.chunking.KnowledgeChunkRepository;
import com.medassist.knowledge.document.ApprovalStatus;
import com.medassist.knowledge.document.IngestionStatus;
import com.medassist.knowledge.document.KnowledgeDocument;
import com.medassist.knowledge.document.KnowledgeDocumentRepository;
import com.medassist.knowledge.embedding.EmbeddingService;
import com.medassist.knowledge.retrieval.RetrievalProperties;
import com.medassist.knowledge.retrieval.VectorRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side of the knowledge base for administrators and knowledge managers. */
@Service
public class KnowledgeQueryService {

    public record SearchHit(UUID chunkId, UUID documentId, String documentTitle, String documentVersion,
                            String sourceOrganization, String section, Integer page, double similarity,
                            String content, String metadataJson, boolean usableForClinicalRetrieval) {
    }

    public record KnowledgeStatus(String embeddingProvider, String embeddingModel, int embeddingDimensions,
                                  ChunkingProperties chunking, RetrievalProperties retrieval, long documents,
                                  long retrievableDocuments, long failedDocuments) {
    }

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final VectorRepository vectorRepository;
    private final EmbeddingService embeddingService;
    private final ChunkingProperties chunkingProperties;
    private final RetrievalProperties retrievalProperties;
    private final AuditService auditService;

    public KnowledgeQueryService(KnowledgeDocumentRepository documentRepository,
                                 KnowledgeChunkRepository chunkRepository, VectorRepository vectorRepository,
                                 EmbeddingService embeddingService, ChunkingProperties chunkingProperties,
                                 RetrievalProperties retrievalProperties, AuditService auditService) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.vectorRepository = vectorRepository;
        this.embeddingService = embeddingService;
        this.chunkingProperties = chunkingProperties;
        this.retrievalProperties = retrievalProperties;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<KnowledgeDocument> list() {
        return documentRepository.findAllByOrderByUploadedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<KnowledgeDocument> versions(UUID documentId) {
        KnowledgeDocument doc = documentRepository.findById(documentId).orElseThrow();
        return documentRepository.findBySeriesIdOrderByUploadedAtDesc(doc.getSeriesId());
    }

    @Transactional(readOnly = true)
    public Page<KnowledgeChunk> chunks(UUID documentId, int page, int size) {
        return chunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
    }

    @Transactional(readOnly = true)
    public long embeddingCount(UUID documentId) {
        return vectorRepository.countEmbeddings(documentId, embeddingService.modelId());
    }

    /** Knowledge-base debugging search: shows exactly what retrieval would return, plus optional inactive sources. */
    @Transactional
    public List<SearchHit> search(String query, int topK, boolean includeInactive) {
        float[] vector = embeddingService.embed(List.of(query)).getFirst();
        List<VectorRepository.VectorMatch> matches = vectorRepository.search(vector, embeddingService.modelId(),
                Math.min(Math.max(topK, 1), 50), includeInactive);
        Map<UUID, KnowledgeChunk> chunks = chunkRepository.findByIdIn(matches.stream()
                .map(VectorRepository.VectorMatch::chunkId).toList()).stream()
                .collect(Collectors.toMap(KnowledgeChunk::getId, Function.identity()));
        Map<UUID, KnowledgeDocument> documents = documentRepository.findAllById(matches.stream()
                .map(VectorRepository.VectorMatch::documentId).distinct().toList()).stream()
                .collect(Collectors.toMap(KnowledgeDocument::getId, Function.identity()));
        List<SearchHit> hits = new ArrayList<>();
        for (VectorRepository.VectorMatch m : matches) {
            KnowledgeChunk c = chunks.get(m.chunkId());
            KnowledgeDocument d = documents.get(m.documentId());
            if (c != null && d != null) {
                hits.add(new SearchHit(c.getId(), d.getId(), d.getTitle(), d.getDocumentVersion(), d.getSourceOrganization(),
                        c.getSection(), c.getPageNumber(), m.similarity(), c.getContent(), c.getMetadata(),
                        d.isUsableForRetrieval() && m.similarity() >= retrievalProperties.minSimilarity()));
            }
        }
        auditService.record(AuditEntry.of(AuditEventType.KNOWLEDGE_RETRIEVAL)
                .entity("KnowledgeBase", "admin-search")
                .with("purpose", "ADMIN_SEARCH")
                .with("embeddingModel", embeddingService.modelId())
                .with("results", hits.stream().map(h -> h.documentId() + "#" + h.chunkId()).toList()));
        return hits;
    }

    @Transactional(readOnly = true)
    public KnowledgeStatus status() {
        return new KnowledgeStatus(embeddingService.providerName(), embeddingService.modelId(),
                embeddingService.dimensions(), chunkingProperties, retrievalProperties, documentRepository.count(),
                documentRepository.countByEnabledTrueAndApprovalStatusAndIngestionStatus(ApprovalStatus.APPROVED,
                        IngestionStatus.INDEXED), documentRepository.countByIngestionStatus(IngestionStatus.FAILED));
    }
}

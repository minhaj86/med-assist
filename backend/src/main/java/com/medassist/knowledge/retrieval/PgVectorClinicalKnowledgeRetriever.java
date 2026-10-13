package com.medassist.knowledge.retrieval;

import com.medassist.common.json.JsonCodec;
import com.medassist.knowledge.chunking.KnowledgeChunk;
import com.medassist.knowledge.chunking.KnowledgeChunkRepository;
import com.medassist.knowledge.document.KnowledgeDocument;
import com.medassist.knowledge.document.KnowledgeDocumentRepository;
import com.medassist.knowledge.embedding.EmbeddingService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;

/**
 * Retrieves passages from approved, enabled, indexed documents only. Results are numbered K1..Kn in rank order.
 */
@Service
public class PgVectorClinicalKnowledgeRetriever implements ClinicalKnowledgeRetriever {

    private final EmbeddingService embeddingService;
    private final VectorRepository vectorRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final RetrievalProperties properties;
    private final JsonCodec json;
    private final Timer timer;

    public PgVectorClinicalKnowledgeRetriever(EmbeddingService embeddingService, VectorRepository vectorRepository,
                                              KnowledgeChunkRepository chunkRepository,
                                              KnowledgeDocumentRepository documentRepository,
                                              RetrievalProperties properties, JsonCodec json,
                                              MeterRegistry meterRegistry) {
        this.embeddingService = embeddingService;
        this.vectorRepository = vectorRepository;
        this.chunkRepository = chunkRepository;
        this.documentRepository = documentRepository;
        this.properties = properties;
        this.json = json;
        this.timer = Timer.builder("rag_query_duration").description("Knowledge retrieval latency")
                .register(meterRegistry);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RetrievedClinicalEvidence> retrieve(KnowledgeQuery query) {
        String text = query.toSearchText();
        if (text.isBlank()) {
            return List.of();
        }
        int limit = query.maxResults() > 0 ? query.maxResults() : properties.topK();
        return timer.record(() -> search(text, limit));
    }

    private List<RetrievedClinicalEvidence> search(String text, int limit) {
        float[] vector = embeddingService.embed(List.of(text)).getFirst();
        List<VectorRepository.VectorMatch> matches = vectorRepository.search(vector, embeddingService.modelId(),
                limit * 4, false);
        Map<UUID, Integer> perDocument = new HashMap<>();
        List<VectorRepository.VectorMatch> selected = new ArrayList<>();
        for (VectorRepository.VectorMatch m : matches) {
            if (m.similarity() < properties.minSimilarity() || selected.size() >= limit) {
                continue;
            }
            if (perDocument.merge(m.documentId(), 1, Integer::sum) <= properties.maxChunksPerDocument()) {
                selected.add(m);
            }
        }
        if (selected.isEmpty()) {
            return List.of();
        }
        Map<UUID, KnowledgeChunk> chunks = chunkRepository.findByIdIn(selected.stream()
                .map(VectorRepository.VectorMatch::chunkId).toList()).stream()
                .collect(Collectors.toMap(KnowledgeChunk::getId, Function.identity()));
        Map<UUID, KnowledgeDocument> documents = documentRepository.findAllById(selected.stream()
                .map(VectorRepository.VectorMatch::documentId).distinct().toList()).stream()
                .collect(Collectors.toMap(KnowledgeDocument::getId, Function.identity()));
        List<RetrievedClinicalEvidence> evidence = new ArrayList<>();
        for (VectorRepository.VectorMatch m : selected) {
            KnowledgeChunk chunk = chunks.get(m.chunkId());
            KnowledgeDocument doc = documents.get(m.documentId());
            if (chunk == null || doc == null) {
                continue;
            }
            evidence.add(new RetrievedClinicalEvidence("K" + (evidence.size() + 1), chunk.getId(), doc.getId(),
                    doc.getTitle(), doc.getSourceOrganization(), chunk.getSection(), chunk.getPageNumber(),
                    m.similarity(), chunk.getContent(), doc.getDocumentVersion(), doc.getPublicationDate(), doc.isSynthetic(),
                    json.read(doc.getConditionTags(), new TypeReference<List<String>>() {
                    })));
        }
        return List.copyOf(evidence);
    }
}

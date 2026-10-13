package com.medassist.knowledge.retrieval;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * pgvector adapter. Vectors of different models/dimensions live in one table and are always filtered by model.
 * Exact cosine search is used (correct and fast for knowledge bases of modest size); see docs/architecture.md for
 * adding a per-model HNSW expression index at scale.
 */
@Repository
public class PgVectorRepository implements VectorRepository {

    private final JdbcClient jdbc;

    public PgVectorRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void store(UUID chunkId, String modelId, float[] vector) {
        jdbc.sql("""
                        insert into knowledge_embeddings (chunk_id, embedding_model, dimensions, embedding, created_at)
                        values (:chunkId, :model, :dims, cast(:vector as vector), now())
                        on conflict (chunk_id, embedding_model) do update
                        set embedding = excluded.embedding, dimensions = excluded.dimensions, created_at = excluded.created_at
                        """)
                .param("chunkId", chunkId)
                .param("model", modelId)
                .param("dims", vector.length)
                .param("vector", toLiteral(vector))
                .update();
    }

    @Override
    public List<VectorMatch> search(float[] query, String modelId, int limit, boolean includeInactive) {
        String activeFilter = includeInactive ? "" : """
                and d.enabled = true and d.approval_status = 'APPROVED' and d.ingestion_status = 'INDEXED'
                """;
        return jdbc.sql("""
                        select e.chunk_id, c.document_id, 1 - (e.embedding <=> cast(:vector as vector)) as similarity
                        from knowledge_embeddings e
                        join knowledge_chunks c on c.id = e.chunk_id
                        join knowledge_documents d on d.id = c.document_id
                        where e.embedding_model = :model and e.dimensions = :dims
                        """ + activeFilter + """
                        order by e.embedding <=> cast(:vector as vector)
                        limit :limit
                        """)
                .param("vector", toLiteral(query))
                .param("model", modelId)
                .param("dims", query.length)
                .param("limit", limit)
                .query((rs, row) -> new VectorMatch(rs.getObject("chunk_id", UUID.class),
                        rs.getObject("document_id", UUID.class), rs.getDouble("similarity")))
                .list();
    }

    @Override
    public long countEmbeddings(UUID documentId, String modelId) {
        return jdbc.sql("""
                        select count(*) from knowledge_embeddings e join knowledge_chunks c on c.id = e.chunk_id
                        where c.document_id = :documentId and e.embedding_model = :model
                        """)
                .param("documentId", documentId)
                .param("model", modelId)
                .query(Long.class).single();
    }

    static String toLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 10).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            if (!Float.isFinite(vector[i])) {
                throw new IllegalArgumentException("Embedding contains a non-finite value");
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }
}

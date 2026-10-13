package com.medassist.knowledge.chunking;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, UUID> {

    Page<KnowledgeChunk> findByDocumentIdOrderByChunkIndexAsc(UUID documentId, Pageable pageable);

    List<KnowledgeChunk> findByIdIn(Collection<UUID> ids);

    @Modifying
    @Query("delete from KnowledgeChunk c where c.documentId = :documentId")
    int deleteByDocumentId(@Param("documentId") UUID documentId);
}

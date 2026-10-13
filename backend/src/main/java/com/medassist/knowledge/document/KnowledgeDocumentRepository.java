package com.medassist.knowledge.document;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, UUID> {

    List<KnowledgeDocument> findAllByOrderByUploadedAtDesc();

    List<KnowledgeDocument> findBySeriesIdOrderByUploadedAtDesc(UUID seriesId);

    boolean existsByChecksumAndEnabledTrue(String checksum);

    boolean existsBySeriesIdAndDocumentVersion(UUID seriesId, String version);

    long countByIngestionStatus(IngestionStatus status);

    long countByEnabledTrueAndApprovalStatusAndIngestionStatus(ApprovalStatus approvalStatus, IngestionStatus status);

    boolean existsBySyntheticTrue();
}

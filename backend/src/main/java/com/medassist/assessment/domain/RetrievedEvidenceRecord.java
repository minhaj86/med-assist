package com.medassist.assessment.domain;

import com.medassist.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * Snapshot of a knowledge passage used by an assessment. Stored by value so the assessment stays reproducible even
 * if the source document is later re-indexed, superseded or deleted.
 */
@Entity
@Immutable
@Table(name = "retrieved_evidence")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RetrievedEvidenceRecord extends BaseEntity {

    @Column(name = "evidence_key", nullable = false, length = 10)
    private String evidenceKey;

    @Column(name = "chunk_id", nullable = false)
    private UUID chunkId;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "document_title", nullable = false, length = 300)
    private String documentTitle;

    @Column(name = "document_version", length = 50)
    private String documentVersion;

    @Column(name = "source_organization", length = 300)
    private String sourceOrganization;

    @Column(name = "section", length = 300)
    private String section;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(name = "publication_date")
    private LocalDate publicationDate;

    @Column(name = "similarity", nullable = false, precision = 6, scale = 5)
    private BigDecimal similarity;

    @Column(name = "excerpt", nullable = false, columnDefinition = "text")
    private String excerpt;

    @Column(name = "synthetic_source", nullable = false)
    private boolean syntheticSource;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public RetrievedEvidenceRecord(String evidenceKey, UUID chunkId, UUID documentId, String documentTitle,
                                   String documentVersion, String sourceOrganization, String section,
                                   Integer pageNumber, LocalDate publicationDate, BigDecimal similarity,
                                   String excerpt, boolean syntheticSource, int displayOrder) {
        super(UUID.randomUUID());
        this.evidenceKey = evidenceKey;
        this.chunkId = chunkId;
        this.documentId = documentId;
        this.documentTitle = documentTitle;
        this.documentVersion = documentVersion;
        this.sourceOrganization = sourceOrganization;
        this.section = section;
        this.pageNumber = pageNumber;
        this.publicationDate = publicationDate;
        this.similarity = similarity;
        this.excerpt = excerpt;
        this.syntheticSource = syntheticSource;
        this.displayOrder = displayOrder;
    }
}

package com.medassist.assessment.domain;

import com.medassist.common.domain.BaseEntity;
import com.medassist.diagnosis.differential.SupportStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(name = "diagnostic_evidence")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DiagnosticEvidenceRecord extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", nullable = false, length = 30)
    private EvidenceType evidenceType;

    @Column(name = "statement", nullable = false, length = 2000)
    private String statement;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "fact_keys", nullable = false)
    private String factKeys;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_keys", nullable = false)
    private String evidenceKeys;

    @Enumerated(EnumType.STRING)
    @Column(name = "support_status", nullable = false, length = 20)
    private SupportStatus supportStatus;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public DiagnosticEvidenceRecord(EvidenceType evidenceType, String statement, String factKeysJson,
                                    String evidenceKeysJson, SupportStatus supportStatus, int displayOrder) {
        super(UUID.randomUUID());
        this.evidenceType = evidenceType;
        this.statement = statement;
        this.factKeys = factKeysJson;
        this.evidenceKeys = evidenceKeysJson;
        this.supportStatus = supportStatus;
        this.displayOrder = displayOrder;
    }
}

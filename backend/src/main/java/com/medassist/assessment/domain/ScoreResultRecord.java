package com.medassist.assessment.domain;

import com.medassist.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "clinical_score_results")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScoreResultRecord extends BaseEntity {

    @Column(name = "score_id", nullable = false, length = 60)
    private String scoreId;

    @Column(name = "score_version", nullable = false, length = 30)
    private String scoreVersion;

    /** Full {@code ScoreResult} as JSON (inputs, missing variables, calculation details, interpretation). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result", nullable = false)
    private String result;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public ScoreResultRecord(String scoreId, String scoreVersion, String resultJson, int displayOrder) {
        super(UUID.randomUUID());
        this.scoreId = scoreId;
        this.scoreVersion = scoreVersion;
        this.result = resultJson;
        this.displayOrder = displayOrder;
    }
}

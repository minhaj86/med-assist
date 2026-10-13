package com.medassist.assessment.application;

import com.medassist.common.domain.DataProvenance;
import com.medassist.diagnosis.context.ClinicalContext;
import com.medassist.diagnosis.context.FactCategory;
import java.util.List;
import java.util.UUID;

/** The exact clinical facts an assessment version was computed from (stored as JSON with a SHA-256 hash). */
public record ContextSnapshot(int ageYears, String biologicalSex, String encounterType, List<SnapshotFact> facts) {

    public record SnapshotFact(String key, FactCategory category, String description, String sourceType,
                               UUID sourceId, String identity, DataProvenance provenance) {
    }

    public static ContextSnapshot of(ClinicalContext context) {
        return new ContextSnapshot(context.ageYears(), context.biologicalSex().name(),
                context.encounterType().name(),
                context.facts().stream()
                        .map(f -> new SnapshotFact(f.key(), f.category(), f.description(), f.sourceType(), f.sourceId(),
                                f.identity(), f.provenance()))
                        .toList());
    }
}

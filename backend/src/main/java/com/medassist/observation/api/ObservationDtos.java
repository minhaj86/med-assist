package com.medassist.observation.api;

import com.medassist.common.api.CommonDtos;
import com.medassist.common.domain.DataProvenance;
import com.medassist.observation.domain.AbnormalFlag;
import com.medassist.observation.domain.AbnormalFlagSource;
import com.medassist.observation.domain.ConsciousnessLevel;
import com.medassist.observation.domain.ObservationCategory;
import com.medassist.observation.domain.VitalSignType;
import com.medassist.terminology.domain.CodingSystem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ObservationDtos {

    private ObservationDtos() {
    }

    public record MeasurementRequest(@NotNull VitalSignType type, BigDecimal value,
                                     ConsciousnessLevel consciousnessLevel) {
    }

    public record RecordVitalSignsRequest(@PastOrPresent Instant measuredAt,
                                          @NotEmpty @Size(max = 10) List<@Valid MeasurementRequest> measurements,
                                          DataProvenance source,
                                          @Size(max = 200) String sourceDetail) {
    }

    public record ObservationResponse(UUID id, UUID encounterId, ObservationCategory category,
                                      VitalSignType vitalSignType, CommonDtos.ConceptDto concept,
                                      BigDecimal numericValue, String textValue, String unit, String displayValue,
                                      Instant measuredAt, CommonDtos.ProvenanceDto provenance,
                                      boolean enteredInError, String enteredInErrorReason) {
    }

    public record RecordLabRequest(@NotBlank @Size(max = 200) String testName,
                                   CodingSystem codingSystem,
                                   @Size(max = 50) String testCode,
                                   BigDecimal numericValue,
                                   @Size(max = 500) String textValue,
                                   @Size(max = 50) String unit,
                                   BigDecimal referenceRangeLow,
                                   BigDecimal referenceRangeHigh,
                                   AbnormalFlag abnormalFlag,
                                   @NotNull @PastOrPresent Instant collectedAt,
                                   DataProvenance source,
                                   @Size(max = 200) String sourceDetail) {
    }

    public record LabResponse(UUID id, UUID encounterId, String testCode, CodingSystem codingSystem, String testName,
                              String codeVerification, BigDecimal numericValue, String textValue, String unit,
                              BigDecimal referenceRangeLow, BigDecimal referenceRangeHigh, AbnormalFlag abnormalFlag,
                              AbnormalFlagSource abnormalFlagSource, String displayValue, Instant collectedAt,
                              CommonDtos.ProvenanceDto provenance, boolean enteredInError,
                              String enteredInErrorReason) {
    }
}

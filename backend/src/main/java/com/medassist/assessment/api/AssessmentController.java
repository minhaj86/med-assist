package com.medassist.assessment.api;

import com.medassist.assessment.application.AssessmentQueryService;
import com.medassist.assessment.application.AssessmentService;
import com.medassist.assessment.application.AssessmentViews;
import com.medassist.assessment.application.FeedbackService;
import com.medassist.assessment.application.FinalAssessmentService;
import com.medassist.assessment.domain.ClinicianFeedback;
import com.medassist.assessment.domain.FeedbackAction;
import com.medassist.assessment.domain.FinalDiagnosis;
import com.medassist.assessment.domain.SuggestionType;
import com.medassist.common.error.NotFoundException;
import com.medassist.security.annotation.RequireClinician;
import com.medassist.terminology.domain.CodingSystem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Clinical assessment")
@RequireClinician
public class AssessmentController {

    public record RunAssessmentRequest(Boolean includeAi) {
    }

    public record FeedbackRequest(@NotNull SuggestionType suggestionType, @NotNull UUID suggestionId,
                                  @NotNull FeedbackAction action, @Size(max = 2000) String reason,
                                  @Size(max = 2000) String modifiedText) {
    }

    public record FeedbackResponse(UUID id, UUID assessmentVersionId, SuggestionType suggestionType,
                                   UUID suggestionId, String clinicianId, FeedbackAction action, String reason,
                                   String modifiedText, Instant createdAt) {
    }

    public record DiagnosisRequest(@NotBlank @Size(max = 300) String conditionName, CodingSystem codingSystem,
                                   @Size(max = 50) String code, @NotNull FinalDiagnosis.DiagnosisCertainty certainty,
                                   UUID sourceSuggestionId) {
    }

    public record FinalizeRequest(UUID reviewedVersionId,
                                  @NotBlank @Size(max = 4000) String summary,
                                  @Size(max = 4000) String plan,
                                  @NotNull @Size(max = 20) List<@Valid DiagnosisRequest> diagnoses,
                                  @NotNull(message = "explicit clinician attestation is required")
                                  @AssertTrue(message = "explicit clinician attestation is required") Boolean attestation) {
    }

    private final AssessmentService assessmentService;
    private final AssessmentQueryService queryService;
    private final FeedbackService feedbackService;
    private final FinalAssessmentService finalAssessmentService;

    public AssessmentController(AssessmentService assessmentService, AssessmentQueryService queryService,
                                FeedbackService feedbackService, FinalAssessmentService finalAssessmentService) {
        this.assessmentService = assessmentService;
        this.queryService = queryService;
        this.feedbackService = feedbackService;
        this.finalAssessmentService = finalAssessmentService;
    }

    @PostMapping("/api/encounters/{encounterId}/assessment")
    @Operation(summary = "Start a clinical assessment (asynchronous); poll the returned run for progress")
    public ResponseEntity<AssessmentViews.RunView> run(@PathVariable UUID encounterId,
                                                       @RequestBody(required = false) RunAssessmentRequest request) {
        boolean includeAi = request == null || request.includeAi() == null || request.includeAi();
        var run = assessmentService.requestAssessment(encounterId, includeAi);
        return ResponseEntity.accepted().location(URI.create("/api/assessment-runs/" + run.getId()))
                .body(queryService.run(run.getId()));
    }

    @GetMapping("/api/assessment-runs/{runId}")
    @Operation(summary = "Assessment run status and completed stages")
    public AssessmentViews.RunView runStatus(@PathVariable UUID runId) {
        return queryService.run(runId);
    }

    @GetMapping("/api/encounters/{encounterId}/assessment-runs/latest")
    @Operation(summary = "Most recent assessment run for the encounter")
    public AssessmentViews.RunView latestRun(@PathVariable UUID encounterId) {
        return queryService.latestRun(encounterId)
                .orElseThrow(() -> new NotFoundException("Assessment run for encounter", encounterId));
    }

    @GetMapping("/api/encounters/{encounterId}/assessments")
    @Operation(summary = "Immutable assessment versions for the encounter (newest first)")
    public List<AssessmentViews.VersionSummary> versions(@PathVariable UUID encounterId) {
        return queryService.versions(encounterId);
    }

    @GetMapping("/api/assessments/{versionId}")
    @Operation(summary = "One immutable assessment version with alerts, differentials, evidence and changes")
    public AssessmentViews.VersionView version(@PathVariable UUID versionId) {
        return queryService.version(versionId);
    }

    @PostMapping("/api/assessments/{versionId}/feedback")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record clinician feedback on an AI suggestion (never used for automatic model training)")
    public FeedbackResponse feedback(@PathVariable UUID versionId, @Valid @RequestBody FeedbackRequest r) {
        ClinicianFeedback f = feedbackService.record(versionId, new FeedbackService.FeedbackCommand(r.suggestionType(),
                r.suggestionId(), r.action(), r.reason(), r.modifiedText()));
        return new FeedbackResponse(f.getId(), f.getAssessmentVersionId(), f.getSuggestionType(), f.getSuggestionId(),
                f.getClinicianId(), f.getAction(), f.getReason(), f.getModifiedText(), f.getCreatedAt());
    }

    @GetMapping("/api/encounters/{encounterId}/final-assessment")
    @Operation(summary = "The clinician's final assessment, if documented")
    public AssessmentViews.FinalAssessmentView finalAssessment(@PathVariable UUID encounterId) {
        return queryService.finalAssessment(encounterId)
                .orElseThrow(() -> new NotFoundException("Final assessment for encounter", encounterId));
    }

    @PostMapping("/api/encounters/{encounterId}/final-assessment")
    @Operation(summary = "Document and finalize the clinical assessment (explicit, attested clinician action)")
    public AssessmentViews.FinalAssessmentView finalizeAssessment(@PathVariable UUID encounterId,
                                                                  @Valid @RequestBody FinalizeRequest r) {
        finalAssessmentService.finalizeAssessment(encounterId, new FinalAssessmentService.FinalizeCommand(
                r.reviewedVersionId(), r.summary(), r.plan(), r.diagnoses().stream()
                .map(d -> new FinalAssessmentService.DiagnosisInput(d.conditionName(), d.codingSystem(), d.code(),
                        d.certainty(), d.sourceSuggestionId())).toList(), r.attestation()));
        return queryService.finalAssessment(encounterId).orElseThrow();
    }
}

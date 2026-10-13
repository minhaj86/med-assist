package com.medassist.assessment.application;

import com.medassist.assessment.domain.Assessment;
import com.medassist.assessment.domain.AssessmentRepository;
import com.medassist.assessment.domain.AssessmentVersion;
import com.medassist.assessment.domain.AssessmentVersionRepository;
import com.medassist.assessment.domain.ClinicianFeedback;
import com.medassist.assessment.domain.ClinicianFeedbackRepository;
import com.medassist.assessment.domain.DifferentialDiagnosisRecord;
import com.medassist.assessment.domain.FeedbackAction;
import com.medassist.assessment.domain.ProposedBy;
import com.medassist.assessment.domain.SuggestedInvestigationRecord;
import com.medassist.assessment.domain.SuggestionType;
import com.medassist.audit.application.AuditEntry;
import com.medassist.audit.application.AuditService;
import com.medassist.audit.domain.AuditEventType;
import com.medassist.common.domain.AlertOrigin;
import com.medassist.common.error.DomainRuleViolationException;
import com.medassist.common.error.NotFoundException;
import com.medassist.common.json.JsonCodec;
import com.medassist.security.CurrentUserProvider;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;

/** Records clinician decisions on AI suggestions (append-only) and publishes them for other modules. */
@Service
public class FeedbackService {

    public record FeedbackCommand(SuggestionType suggestionType, UUID suggestionId, FeedbackAction action,
                                  String reason, String modifiedText) {
    }

    private record Suggestion(String name, String reason, List<String> evidenceKeys, ProposedBy proposedBy) {
    }

    private final AssessmentVersionRepository versionRepository;
    private final AssessmentRepository assessmentRepository;
    private final ClinicianFeedbackRepository feedbackRepository;
    private final ApplicationEventPublisher events;
    private final AuditService auditService;
    private final CurrentUserProvider currentUser;
    private final JsonCodec json;
    private final Clock clock;

    public FeedbackService(AssessmentVersionRepository versionRepository, AssessmentRepository assessmentRepository,
                           ClinicianFeedbackRepository feedbackRepository, ApplicationEventPublisher events,
                           AuditService auditService, CurrentUserProvider currentUser, JsonCodec json, Clock clock) {
        this.versionRepository = versionRepository;
        this.assessmentRepository = assessmentRepository;
        this.feedbackRepository = feedbackRepository;
        this.events = events;
        this.auditService = auditService;
        this.currentUser = currentUser;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    public ClinicianFeedback record(UUID versionId, FeedbackCommand command) {
        AssessmentVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> NotFoundException.of("Assessment version", versionId));
        Assessment assessment = assessmentRepository.findById(version.getAssessmentId()).orElseThrow();
        assessment.ensureNotFinalized();
        Suggestion suggestion = locate(version, command.suggestionType(), command.suggestionId());
        if (command.modifiedText() != null && !command.modifiedText().isBlank()
                && command.action() != FeedbackAction.ACCEPTED) {
            throw new DomainRuleViolationException("Modified text can only accompany an ACCEPTED decision");
        }
        String clinicianId = currentUser.currentUserId();
        ClinicianFeedback feedback = feedbackRepository.save(new ClinicianFeedback(versionId, command.suggestionType(),
                command.suggestionId(), clinicianId, command.action(), blankToNull(command.reason()),
                blankToNull(command.modifiedText()), Instant.now(clock)));
        auditService.record(AuditEntry.of(AuditEventType.CLINICIAN_FEEDBACK_RECORDED)
                .entity("ClinicianFeedback", feedback.getId())
                .patient(version.getPatientId())
                .encounter(version.getEncounterId())
                .with("assessmentVersionId", versionId)
                .with("suggestionType", command.suggestionType())
                .with("suggestionId", command.suggestionId())
                .with("action", command.action())
                .with("modified", feedback.getModifiedText() != null));
        events.publishEvent(new SuggestionFeedbackRecorded(versionId, version.getEncounterId(), version.getPatientId(),
                command.suggestionType(), command.suggestionId(), suggestion.name(), suggestion.reason(),
                suggestion.evidenceKeys(), suggestion.proposedBy(), command.action(), clinicianId,
                feedback.getReason()));
        return feedback;
    }

    private Suggestion locate(AssessmentVersion version, SuggestionType type, UUID id) {
        return switch (type) {
            case DIFFERENTIAL -> version.getDifferentials().stream().filter(d -> d.getId().equals(id)).findFirst()
                    .map(d -> new Suggestion(d.getConditionName(), d.getReasoningSummary(), strings(d.getCitations()),
                            ProposedBy.AI))
                    .orElseThrow(() -> NotFoundException.of("Differential suggestion", id));
            case RED_FLAG -> version.getSafetyAlerts().stream()
                    .filter(a -> a.getId().equals(id) && a.getOrigin() == AlertOrigin.AI).findFirst()
                    .map(a -> new Suggestion(a.getTitle(), a.getExplanation(), strings(a.getEvidenceKeys()), ProposedBy.AI))
                    .orElseThrow(() -> NotFoundException.of("AI red flag", id));
            case INVESTIGATION -> version.getDifferentials().stream()
                    .map(DifferentialDiagnosisRecord::getInvestigations).flatMap(List::stream)
                    .filter(i -> i.getId().equals(id)).findFirst()
                    .map(this::toSuggestion)
                    .orElseThrow(() -> NotFoundException.of("Investigation suggestion", id));
        };
    }

    private Suggestion toSuggestion(SuggestedInvestigationRecord i) {
        return new Suggestion(i.getName(), i.getReason(), strings(i.getSupportingEvidenceKeys()), i.getProposedBy());
    }

    private List<String> strings(String jsonArray) {
        return json.read(jsonArray, new TypeReference<List<String>>() {
        });
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

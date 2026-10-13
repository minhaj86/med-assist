package com.medassist.investigation.application;

import com.medassist.assessment.application.SuggestionFeedbackRecorded;
import com.medassist.assessment.domain.FeedbackAction;
import com.medassist.assessment.domain.ProposedBy;
import com.medassist.assessment.domain.SuggestionType;
import com.medassist.audit.application.AuditEntry;
import com.medassist.audit.application.AuditService;
import com.medassist.audit.domain.AuditEventType;
import com.medassist.common.error.NotFoundException;
import com.medassist.common.json.JsonCodec;
import com.medassist.encounter.application.EncounterService;
import com.medassist.encounter.domain.Encounter;
import com.medassist.investigation.domain.Investigation;
import com.medassist.investigation.domain.InvestigationRepository;
import com.medassist.investigation.domain.InvestigationStatus;
import com.medassist.investigation.domain.InvestigationStatusChange;
import com.medassist.investigation.domain.InvestigationStatusChangeRepository;
import com.medassist.security.CurrentUserProvider;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvestigationService {

    private final InvestigationRepository repository;
    private final InvestigationStatusChangeRepository historyRepository;
    private final EncounterService encounterService;
    private final AuditService auditService;
    private final CurrentUserProvider currentUser;
    private final JsonCodec json;
    private final Clock clock;

    public InvestigationService(InvestigationRepository repository,
                                InvestigationStatusChangeRepository historyRepository,
                                EncounterService encounterService, AuditService auditService,
                                CurrentUserProvider currentUser, JsonCodec json, Clock clock) {
        this.repository = repository;
        this.historyRepository = historyRepository;
        this.encounterService = encounterService;
        this.auditService = auditService;
        this.currentUser = currentUser;
        this.json = json;
        this.clock = clock;
    }

    /** A clinician accepting / rejecting a suggested investigation moves it into the workflow. */
    @EventListener
    @Transactional
    public void onSuggestionFeedback(SuggestionFeedbackRecorded event) {
        if (event.suggestionType() != SuggestionType.INVESTIGATION) {
            return;
        }
        InvestigationStatus target = switch (event.action()) {
            case ACCEPTED -> InvestigationStatus.ACCEPTED;
            case REJECTED, NOT_RELEVANT -> InvestigationStatus.REJECTED;
            default -> null;
        };
        if (target == null) {
            return;
        }
        Optional<Investigation> existing = repository.findByEncounterIdAndSourceSuggestionId(event.encounterId(),
                event.suggestionId());
        if (existing.isPresent()) {
            if (existing.get().getStatus() != target && existing.get().getStatus().allowedNext().contains(target)) {
                transition(existing.get(), target, event.feedbackReason());
            }
            return;
        }
        Investigation investigation = repository.save(Investigation.create(event.encounterId(), event.patientId(),
                event.suggestionName(), event.proposedBy(), event.suggestionReason(), json.write(event.evidenceKeys()),
                target, event.suggestionId(), event.assessmentVersionId(), event.clinicianId()));
        recordHistory(investigation, null, target, event.feedbackReason());
    }

    @Transactional
    public Investigation addClinicianInvestigation(UUID encounterId, String name, String reason,
                                                   InvestigationStatus status) {
        Encounter encounter = encounterService.requireModifiable(encounterId);
        Investigation investigation = repository.save(Investigation.create(encounterId, encounter.getPatientId(), name,
                ProposedBy.CLINICIAN, reason, json.write(List.of()), status, null, null, currentUser.currentUserId()));
        recordHistory(investigation, null, status, null);
        return investigation;
    }

    @Transactional
    public Investigation changeStatus(UUID investigationId, InvestigationStatus target, String note) {
        Investigation investigation = repository.findById(investigationId)
                .orElseThrow(() -> NotFoundException.of("Investigation", investigationId));
        transition(investigation, target, note);
        return investigation;
    }

    @Transactional(readOnly = true)
    public List<Investigation> list(UUID encounterId) {
        encounterService.require(encounterId);
        return repository.findByEncounterIdOrderByCreatedAtAsc(encounterId);
    }

    @Transactional(readOnly = true)
    public List<InvestigationStatusChange> history(UUID investigationId) {
        return historyRepository.findByInvestigationIdOrderByChangedAtAsc(investigationId);
    }

    private void transition(Investigation investigation, InvestigationStatus target, String note) {
        InvestigationStatus previous = investigation.transitionTo(target);
        recordHistory(investigation, previous, target, note);
    }

    private void recordHistory(Investigation investigation, InvestigationStatus from, InvestigationStatus to,
                               String note) {
        historyRepository.save(new InvestigationStatusChange(investigation.getId(), from, to,
                currentUser.currentUserId(), Instant.now(clock), note));
        auditService.record(AuditEntry.of(AuditEventType.INVESTIGATION_STATUS_CHANGED)
                .entity("Investigation", investigation.getId())
                .patient(investigation.getPatientId())
                .encounter(investigation.getEncounterId())
                .with("from", from)
                .with("to", to)
                .with("proposedBy", investigation.getProposedBy()));
    }
}

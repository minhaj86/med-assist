package com.medassist.patient.application;

import com.medassist.audit.application.AuditEntry;
import com.medassist.audit.application.AuditService;
import com.medassist.audit.domain.AuditEventType;
import com.medassist.common.error.ConflictException;
import com.medassist.common.error.DomainRuleViolationException;
import com.medassist.common.error.NotFoundException;
import com.medassist.patient.domain.Patient;
import com.medassist.patient.domain.PatientRepository;
import com.medassist.security.CurrentUserProvider;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatientService {

    private final PatientRepository repository;
    private final AuditService auditService;
    private final CurrentUserProvider currentUser;
    private final Clock clock;

    public PatientService(PatientRepository repository, AuditService auditService, CurrentUserProvider currentUser,
                          Clock clock) {
        this.repository = repository;
        this.auditService = auditService;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional
    public Patient register(PatientCommands.RegisterPatient command) {
        if (repository.existsByExternalPatientId(command.externalPatientId())) {
            throw new ConflictException("A patient with this external patient identifier already exists");
        }
        validateDateOfBirth(command.dateOfBirth());
        Patient patient = Patient.register(command.externalPatientId(), command.firstName(), command.lastName(),
                command.dateOfBirth(), command.biologicalSex(), command.gender(), command.contactPhone(),
                command.synthetic(), command.provenance(), currentUser.currentUserId());
        repository.save(patient);
        auditService.record(AuditEntry.of(AuditEventType.PATIENT_CREATED)
                .entity("Patient", patient.getId())
                .patient(patient.getId())
                .with("provenance", command.provenance())
                .with("synthetic", command.synthetic()));
        return patient;
    }

    @Transactional
    public Patient updateDemographics(UUID patientId, PatientCommands.UpdateDemographics command) {
        Patient patient = require(patientId);
        validateDateOfBirth(command.dateOfBirth());
        patient.updateDemographics(command.firstName(), command.lastName(), command.dateOfBirth(),
                command.biologicalSex(), command.gender(), command.contactPhone());
        auditService.record(AuditEntry.of(AuditEventType.PATIENT_UPDATED)
                .entity("Patient", patientId)
                .patient(patientId));
        return patient;
    }

    /** Loads a patient for display and records the access in the audit trail. */
    @Transactional
    public Patient view(UUID patientId) {
        Patient patient = require(patientId);
        auditService.record(AuditEntry.of(AuditEventType.PATIENT_VIEWED).entity("Patient", patientId).patient(patientId));
        return patient;
    }

    @Transactional(readOnly = true)
    public Patient require(UUID patientId) {
        return repository.findById(patientId).orElseThrow(() -> NotFoundException.of("Patient", patientId));
    }

    @Transactional(readOnly = true)
    public Page<Patient> search(String query, int page, int size) {
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by("lastName", "firstName"));
        if (query == null || query.isBlank()) {
            return repository.findAll(PageRequest.of(pageRequest.getPageNumber(), pageRequest.getPageSize(),
                    Sort.by(Sort.Direction.DESC, "updatedAt")));
        }
        return repository.search(escapeLike(query.trim().toLowerCase(Locale.ROOT)), pageRequest);
    }

    @Transactional(readOnly = true)
    public List<Patient> findAllById(Collection<UUID> ids) {
        return repository.findAllById(ids);
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    private void validateDateOfBirth(LocalDate dateOfBirth) {
        LocalDate today = LocalDate.now(clock);
        if (dateOfBirth == null || dateOfBirth.isAfter(today) || dateOfBirth.isBefore(today.minusYears(130))) {
            throw new DomainRuleViolationException("Date of birth must be in the past and within 130 years");
        }
    }

    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}

package com.medassist.encounter.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EncounterRepository extends JpaRepository<Encounter, UUID> {

    List<Encounter> findByPatientIdOrderByStartedAtDesc(UUID patientId);

    List<Encounter> findByClinicianIdAndStartedAtGreaterThanEqualOrderByStartedAtDesc(String clinicianId, Instant from);

    List<Encounter> findByClinicianIdOrderByStartedAtDesc(String clinicianId, Pageable pageable);

    List<Encounter> findByClinicianIdAndStatusOrderByStartedAtDesc(String clinicianId, EncounterStatus status,
                                                                     Pageable pageable);
}

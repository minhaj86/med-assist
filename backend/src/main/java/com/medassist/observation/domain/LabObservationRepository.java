package com.medassist.observation.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabObservationRepository extends JpaRepository<LabObservation, UUID> {

    List<LabObservation> findByEncounterIdOrderByCollectedAtDesc(UUID encounterId);
}

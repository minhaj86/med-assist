package com.medassist.symptom.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SymptomRepository extends JpaRepository<Symptom, UUID> {

    List<Symptom> findByEncounterIdOrderByCreatedAtAsc(UUID encounterId);
}

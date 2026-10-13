package com.medassist.medication.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicationRepository extends JpaRepository<Medication, UUID> {

    List<Medication> findByPatientIdOrderByCreatedAtDesc(UUID patientId);
}

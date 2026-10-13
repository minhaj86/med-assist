package com.medassist.history.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicalHistoryRepository extends JpaRepository<MedicalHistoryEntry, UUID> {

    List<MedicalHistoryEntry> findByPatientIdOrderByCategoryAscCreatedAtDesc(UUID patientId);
}

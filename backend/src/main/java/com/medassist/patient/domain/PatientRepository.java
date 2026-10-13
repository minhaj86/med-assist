package com.medassist.patient.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PatientRepository extends JpaRepository<Patient, UUID> {

    Optional<Patient> findByExternalPatientId(String externalPatientId);

    boolean existsByExternalPatientId(String externalPatientId);

    /** Name / identifier search. {@code pattern} must already be LIKE-escaped and lower-cased. */
    @Query("""
            select p from Patient p
            where lower(p.lastName) like concat(:pattern, '%') escape '\\'
               or lower(p.firstName) like concat(:pattern, '%') escape '\\'
               or lower(concat(p.firstName, ' ', p.lastName)) like concat('%', :pattern, '%') escape '\\'
               or lower(concat(p.lastName, ', ', p.firstName)) like concat('%', :pattern, '%') escape '\\'
               or lower(p.externalPatientId) like concat(:pattern, '%') escape '\\'
            """)
    Page<Patient> search(@Param("pattern") String pattern, Pageable pageable);
}

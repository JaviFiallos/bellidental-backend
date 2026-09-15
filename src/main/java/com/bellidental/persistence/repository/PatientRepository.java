package com.bellidental.persistence.repository;

import com.bellidental.persistence.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PatientRepository extends JpaRepository<Patient, UUID> {

    boolean existsByDocumentNumberAndDeletedAtIsNull(String documentNumber);

    Optional<Patient> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Patient> findByDocumentNumberAndDeletedAtIsNull(String documentNumber);

    @Query("SELECT p FROM Patient p WHERE p.deletedAt IS NULL ORDER BY p.lastName, p.firstName")
    List<Patient> findAllActive();
}

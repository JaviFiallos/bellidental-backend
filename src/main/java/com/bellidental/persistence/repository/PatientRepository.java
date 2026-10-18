package com.bellidental.persistence.repository;

import com.bellidental.persistence.entity.Patient;
import com.bellidental.shared.enums.DocumentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PatientRepository extends JpaRepository<Patient, UUID> {

    boolean existsByDocumentTypeAndDocumentNumber(DocumentType documentType, String documentNumber);

    Optional<Patient> findByIdAndDeletedAtIsNull(UUID id);

    List<Patient> findByIdInAndDeletedAtIsNull(Collection<UUID> ids);

    Optional<Patient> findByDocumentNumberAndDeletedAtIsNull(String documentNumber);

    @Query("SELECT p FROM Patient p WHERE p.deletedAt IS NULL AND p.active = true AND " +
           "(p.documentNumber LIKE CONCAT(:query, '%') OR p.phoneE164 LIKE :queryPrefix) " +
           "ORDER BY p.lastName, p.firstName")
    Page<Patient> findActiveByDocumentOrPhonePrefix(@Param("query") String query, @Param("queryPrefix") String queryPrefix, Pageable pageable);

    @Query("SELECT p FROM Patient p WHERE p.deletedAt IS NULL AND p.active = true AND " +
           "(p.firstName ILIKE %:query% OR p.lastName ILIKE %:query%) " +
           "ORDER BY p.lastName, p.firstName")
    Page<Patient> findActiveByNameOrLastName(@Param("query") String query, Pageable pageable);

    List<Patient> findByPhoneE164AndActiveTrueAndDeletedAtIsNull(String phoneE164);

    Optional<Patient> findByDocumentTypeAndDocumentNumberAndDeletedAtIsNull(DocumentType documentType, String documentNumber);
}

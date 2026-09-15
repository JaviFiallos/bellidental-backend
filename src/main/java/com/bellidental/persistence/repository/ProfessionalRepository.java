package com.bellidental.persistence.repository;

import com.bellidental.persistence.entity.Professional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProfessionalRepository extends JpaRepository<Professional, UUID> {

    boolean existsByDocumentNumberAndDeletedAtIsNull(String documentNumber);

    boolean existsByLicenseNumberAndDeletedAtIsNull(String licenseNumber);

    Optional<Professional> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Professional> findByDocumentNumberAndDeletedAtIsNull(String documentNumber);

    @Query("SELECT p FROM Professional p WHERE p.deletedAt IS NULL ORDER BY p.lastName, p.firstName")
    List<Professional> findAllActive();
}

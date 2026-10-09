package com.bellidental.persistence.repository;

import com.bellidental.persistence.entity.ProfessionalSpecialty;
import com.bellidental.persistence.entity.ProfessionalSpecialtyId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProfessionalSpecialtyRepository extends JpaRepository<ProfessionalSpecialty, ProfessionalSpecialtyId> {
    List<ProfessionalSpecialty> findByProfessionalId(UUID professionalId);
}

package com.bellidental.persistence.repository;

import com.bellidental.persistence.entity.ProfessionalSpecialty;
import com.bellidental.persistence.entity.ProfessionalSpecialtyId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProfessionalSpecialtyRepository extends JpaRepository<ProfessionalSpecialty, ProfessionalSpecialtyId> {
}

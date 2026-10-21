package com.bellidental.persistence.repository;

import com.bellidental.persistence.entity.ProfessionalSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProfessionalScheduleRepository extends JpaRepository<ProfessionalSchedule, Long> {
    
    List<ProfessionalSchedule> findByProfessionalIdAndActiveTrue(UUID professionalId);
}

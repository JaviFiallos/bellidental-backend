package com.bellidental.persistence.repository;

import com.bellidental.persistence.entity.MedicalHistoryCondition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MedicalHistoryConditionRepository extends JpaRepository<MedicalHistoryCondition, Long> {
    
    List<MedicalHistoryCondition> findByActiveTrueOrderByDisplayOrderAsc();
}

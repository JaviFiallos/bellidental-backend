package com.bellidental.persistence.repository;

import com.bellidental.persistence.entity.DentalCondition;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DentalConditionRepository extends JpaRepository<DentalCondition, Long> {
}

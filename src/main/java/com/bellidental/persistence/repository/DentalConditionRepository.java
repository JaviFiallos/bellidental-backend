package com.bellidental.persistence.repository;

import com.bellidental.persistence.entity.DentalCondition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DentalConditionRepository extends JpaRepository<DentalCondition, Long> {

    Optional<DentalCondition> findByCode(String code);
}

package com.bellidental.persistence.repository;

import com.bellidental.persistence.entity.DentalConditionSymbol;
import org.springframework.data.jpa.repository.JpaRepository;

import com.bellidental.shared.enums.FindingStatus;

public interface DentalConditionSymbolRepository extends JpaRepository<DentalConditionSymbol, Long> {
    boolean existsByDentalConditionIdAndStatus(Long dentalConditionId, FindingStatus status);
}

package com.bellidental.persistence.repository;

import com.bellidental.persistence.entity.Treatment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface TreatmentRepository extends JpaRepository<Treatment, Long> {

    List<Treatment> findByActiveTrueOrderByCodeAsc();

    List<Treatment> findAllByOrderByCodeAsc();

    boolean existsByCodeIgnoreCase(String code);

    List<Treatment> findByCodeInAndActiveTrue(Collection<String> codes);

    @Query(value = "SELECT * FROM treatment_catalog WHERE active = true " +
            "AND (code ILIKE :query || '%' OR name ILIKE '%' || :query || '%') ORDER BY code",
            nativeQuery = true)
    List<Treatment> searchActiveByCodeOrName(@Param("query") String query);
}

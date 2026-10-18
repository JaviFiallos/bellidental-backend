package com.bellidental.persistence.repository;

import com.bellidental.persistence.entity.Medication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MedicationRepository extends JpaRepository<Medication, Long> {

    @Query(value = "SELECT * FROM medications WHERE active = true " +
            "AND (name ILIKE :query || '%' OR (LENGTH(:query) >= 3 AND name % :query)) " +
            "ORDER BY (name ILIKE :query || '%') DESC, name " +
            "LIMIT 20",
            nativeQuery = true)
    List<Medication> searchActive(@Param("query") String query);
}

package com.bellidental.persistence.repository;

import com.bellidental.persistence.entity.Specialty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpecialtyRepository extends JpaRepository<Specialty, Long> {
    List<Specialty> findByActiveTrueOrderByName();

    List<Specialty> findAllByOrderByName();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    @Query(value = "SELECT COUNT(*) FROM appointments WHERE specialty_id = :specialtyId " +
            "AND status IN ('PENDING', 'CONFIRMED') AND start_at > now()", nativeQuery = true)
    long countFutureActiveAppointments(@Param("specialtyId") Long specialtyId);
}

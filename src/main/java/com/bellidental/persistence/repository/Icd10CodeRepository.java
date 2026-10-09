package com.bellidental.persistence.repository;

import com.bellidental.persistence.entity.Icd10Code;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface Icd10CodeRepository extends JpaRepository<Icd10Code, Long> {

    @Query(value = "SELECT * FROM icd10_codes WHERE active = true AND code LIKE :prefix || '%' " +
            "ORDER BY code LIMIT 20", nativeQuery = true)
    List<Icd10Code> findActiveByCodePrefix(@Param("prefix") String prefix);

    @Query(value = "SELECT * FROM icd10_codes WHERE active = true " +
            "AND (description ILIKE '%' || :text || '%' OR description % :text) " +
            "ORDER BY similarity(description, :text) DESC, code LIMIT 20", nativeQuery = true)
    List<Icd10Code> findActiveByDescription(@Param("text") String text);
}

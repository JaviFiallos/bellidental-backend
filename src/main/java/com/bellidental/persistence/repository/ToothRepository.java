package com.bellidental.persistence.repository;

import com.bellidental.persistence.entity.Tooth;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ToothRepository extends JpaRepository<Tooth, Short> {
}

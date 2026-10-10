package com.bellidental.persistence.repository;

import com.bellidental.persistence.entity.ToothSurface;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ToothSurfaceRepository extends JpaRepository<ToothSurface, String> {
}

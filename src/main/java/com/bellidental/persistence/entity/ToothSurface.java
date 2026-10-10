package com.bellidental.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tooth_surfaces")
public class ToothSurface {

    @Id
    @Column(name = "surface_code", columnDefinition = "bpchar")
    private String surfaceCode;

    @Column(nullable = false, length = 30)
    private String name;

    public String getSurfaceCode() {
        return surfaceCode;
    }

    public void setSurfaceCode(String surfaceCode) {
        this.surfaceCode = surfaceCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

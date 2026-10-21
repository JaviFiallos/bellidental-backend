package com.bellidental.persistence.entity;

import com.bellidental.shared.enums.Arch;
import com.bellidental.shared.enums.Dentition;
import com.bellidental.shared.enums.ToothType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "teeth")
public class Tooth {

    @Id
    @Column(name = "tooth_code")
    private Short toothCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Dentition dentition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private Arch arch;

    @Column(nullable = false)
    private Short quadrant;

    @Column(nullable = false)
    private Short position;

    @Enumerated(EnumType.STRING)
    @Column(name = "tooth_type", nullable = false, length = 10)
    private ToothType toothType;

    public Short getToothCode() {
        return toothCode;
    }

    public void setToothCode(Short toothCode) {
        this.toothCode = toothCode;
    }

    public Dentition getDentition() {
        return dentition;
    }

    public void setDentition(Dentition dentition) {
        this.dentition = dentition;
    }

    public Arch getArch() {
        return arch;
    }

    public void setArch(Arch arch) {
        this.arch = arch;
    }

    public Short getQuadrant() {
        return quadrant;
    }

    public void setQuadrant(Short quadrant) {
        this.quadrant = quadrant;
    }

    public Short getPosition() {
        return position;
    }

    public void setPosition(Short position) {
        this.position = position;
    }

    public ToothType getToothType() {
        return toothType;
    }

    public void setToothType(ToothType toothType) {
        this.toothType = toothType;
    }
}

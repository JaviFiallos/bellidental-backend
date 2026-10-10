package com.bellidental.persistence.entity;

import com.bellidental.shared.enums.FindingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "dental_condition_symbols")
public class DentalConditionSymbol {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "dental_condition_symbol_id")
    private Long dentalConditionSymbolId;

    @Column(name = "dental_condition_id", nullable = false)
    private Long dentalConditionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private FindingStatus status;

    @Column(nullable = false, length = 24)
    private String shape;

    @Column(nullable = false, columnDefinition = "bpchar")
    private String color;

    @Column(name = "secondary_color", columnDefinition = "bpchar")
    private String secondaryColor;

    @Column(nullable = false)
    private Boolean filled;

    @Column(name = "line_style", nullable = false, length = 6)
    private String lineStyle;

    public Long getDentalConditionSymbolId() {
        return dentalConditionSymbolId;
    }

    public void setDentalConditionSymbolId(Long dentalConditionSymbolId) {
        this.dentalConditionSymbolId = dentalConditionSymbolId;
    }

    public Long getDentalConditionId() {
        return dentalConditionId;
    }

    public void setDentalConditionId(Long dentalConditionId) {
        this.dentalConditionId = dentalConditionId;
    }

    public FindingStatus getStatus() {
        return status;
    }

    public void setStatus(FindingStatus status) {
        this.status = status;
    }

    public String getShape() {
        return shape;
    }

    public void setShape(String shape) {
        this.shape = shape;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getSecondaryColor() {
        return secondaryColor;
    }

    public void setSecondaryColor(String secondaryColor) {
        this.secondaryColor = secondaryColor;
    }

    public Boolean getFilled() {
        return filled;
    }

    public void setFilled(Boolean filled) {
        this.filled = filled;
    }

    public String getLineStyle() {
        return lineStyle;
    }

    public void setLineStyle(String lineStyle) {
        this.lineStyle = lineStyle;
    }
}

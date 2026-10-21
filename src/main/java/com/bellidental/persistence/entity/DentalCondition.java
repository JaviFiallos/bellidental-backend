package com.bellidental.persistence.entity;

import com.bellidental.shared.enums.ConditionCategory;
import com.bellidental.shared.enums.ConditionScope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "dental_conditions")
public class DentalCondition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "dental_condition_id")
    private Long dentalConditionId;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ConditionCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private ConditionScope scope;

    @Column(name = "grade_min")
    private Short gradeMin;

    @Column(name = "grade_max")
    private Short gradeMax;

    @Column(name = "cpo_component", columnDefinition = "bpchar")
    private String cpoComponent;

    @Column(name = "ceo_component", columnDefinition = "bpchar")
    private String ceoComponent;

    @Column(nullable = false)
    private Boolean active;

    public Long getDentalConditionId() {
        return dentalConditionId;
    }

    public void setDentalConditionId(Long dentalConditionId) {
        this.dentalConditionId = dentalConditionId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ConditionCategory getCategory() {
        return category;
    }

    public void setCategory(ConditionCategory category) {
        this.category = category;
    }

    public ConditionScope getScope() {
        return scope;
    }

    public void setScope(ConditionScope scope) {
        this.scope = scope;
    }

    public Short getGradeMin() {
        return gradeMin;
    }

    public void setGradeMin(Short gradeMin) {
        this.gradeMin = gradeMin;
    }

    public Short getGradeMax() {
        return gradeMax;
    }

    public void setGradeMax(Short gradeMax) {
        this.gradeMax = gradeMax;
    }

    public String getCpoComponent() {
        return cpoComponent;
    }

    public void setCpoComponent(String cpoComponent) {
        this.cpoComponent = cpoComponent;
    }

    public String getCeoComponent() {
        return ceoComponent;
    }

    public void setCeoComponent(String ceoComponent) {
        this.ceoComponent = ceoComponent;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}

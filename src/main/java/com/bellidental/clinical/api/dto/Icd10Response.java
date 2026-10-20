package com.bellidental.clinical.api.dto;

public record Icd10Response(
    Long id,
    String code,
    String description,
    String category,
    String includedTerms
) {
}

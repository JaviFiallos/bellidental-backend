package com.bellidental.audit.api.dto;

public record FieldChangeResponse(
        String field,
        String oldValue,
        String newValue
) {}

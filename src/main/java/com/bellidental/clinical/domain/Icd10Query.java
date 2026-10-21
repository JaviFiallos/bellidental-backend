package com.bellidental.clinical.domain;

import com.bellidental.shared.exception.InvalidInputException;

import java.util.regex.Pattern;

/** Búsqueda CIE-10: por código si parece código (letra + dígito), por texto en otro caso. */
public record Icd10Query(String value, boolean codeSearch) {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z]\\d");
    private static final int MIN_LENGTH = 2;

    public static Icd10Query parse(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.length() < MIN_LENGTH) {
            throw new InvalidInputException("La búsqueda CIE-10 requiere al menos 2 caracteres.");
        }
        if (CODE_PATTERN.matcher(trimmed).find()) {
            return new Icd10Query(trimmed.toUpperCase(), true);
        }
        return new Icd10Query(trimmed, false);
    }
}

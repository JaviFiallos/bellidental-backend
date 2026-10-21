package com.bellidental.audit.domain;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Compara dos fotos de una entidad (campo → valor en texto) y devuelve solo los campos que cambian. */
public final class AuditDiff {

    private AuditDiff() {}

    public record FieldChange(String field, String oldValue, String newValue) {}

    public static List<FieldChange> diff(Map<String, String> before, Map<String, String> after) {
        Set<String> fields = new TreeSet<>(before.keySet());
        fields.addAll(after.keySet());
        return fields.stream()
                .filter(field -> !Objects.equals(before.get(field), after.get(field)))
                .map(field -> new FieldChange(field, before.get(field), after.get(field)))
                .toList();
    }
}

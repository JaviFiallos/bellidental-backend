package com.bellidental.shared.validation;

import com.bellidental.shared.exception.InvalidInputException;

/**
 * Normaliza números de teléfono al formato internacional E.164.
 */
public class PhoneNumberNormalizer {

    public static String normalize(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }

        // Eliminar espacios, guiones, paréntesis y símbolos más del principio temporalmente
        String cleaned = phone.replaceAll("[\\s\\-()+]+", "");

        if (cleaned.startsWith("09") && cleaned.length() == 10) {
            return "593" + cleaned.substring(1);
        }

        if (cleaned.startsWith("5939") && cleaned.length() == 12) {
            return cleaned;
        }

        // Permitir teléfonos fijos de ecuador (ej 02) -> 5932
        if (cleaned.startsWith("0") && cleaned.length() >= 9) {
            return "593" + cleaned.substring(1);
        }

        // Si empieza con 593 pero no es 9 (es decir, fijo normalizado)
        if (cleaned.matches("593\\d{8,9}")) {
            return cleaned;
        }

        throw new InvalidInputException("Formato de teléfono no reconocible");
    }
}

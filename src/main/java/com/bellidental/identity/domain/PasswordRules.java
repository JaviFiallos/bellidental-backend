package com.bellidental.identity.domain;

import com.bellidental.shared.exception.InvalidInputException;

import java.util.Locale;

/** Reglas puras de contraseñas y nombres de usuario. */
public final class PasswordRules {

    private static final int MIN_PASSWORD_LENGTH = 10;
    private static final int MAX_USERNAME_LENGTH = 60;

    private PasswordRules() {}

    public static void validate(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new InvalidInputException("La contraseña debe tener al menos 10 caracteres.");
        }
    }

    public static String usernameFromEmail(String email) {
        int at = email.indexOf('@');
        String localPart = at >= 0 ? email.substring(0, at) : email;
        String cleaned = localPart.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "");
        return cleaned.length() > MAX_USERNAME_LENGTH ? cleaned.substring(0, MAX_USERNAME_LENGTH) : cleaned;
    }
}

package com.bellidental.shared.exception;

/** Falta un permiso de negocio concreto (p. ej. ver saldos): 403 con su mensaje. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}

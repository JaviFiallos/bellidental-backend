package com.bellidental.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Excepción lanzada cuando un recurso no existe o ya fue eliminado (soft delete).
 * Resulta en HTTP 404.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Object id) {
        super(String.format("%s con id '%s' no fue encontrado.", resource, id));
    }

    public ResourceNotFoundException(String message) {
        super(message);
    }
}

package com.bellidental.shared.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

/**
 * Envoltorio estándar para todas las respuestas de la API.
 *
 * <pre>
 * { "success": true, "message": "...", "data": {...} }
 * </pre>
 *
 * @param <T> Tipo del cuerpo de la respuesta.
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final String message;
    private final String code;
    private final T data;

    private ApiResponse(boolean success, String message, String code, T data) {
        this.success = success;
        this.message = message;
        this.code = code;
        this.data = data;
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, null, data);
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, null, null, data);
    }

    public static ApiResponse<Void> ok(String message) {
        return new ApiResponse<>(true, message, null, null);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null, null);
    }

    public static <T> ApiResponse<T> error(String message, String code) {
        return new ApiResponse<>(false, message, code, null);
    }
}

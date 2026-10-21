package com.bellidental.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de OpenAPI 3 (Swagger UI).
 * El esquema de seguridad JWT se aplica globalmente — basta con pegar el token en "Authorize".
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Bellidental API",
                version = "v1",
                description = "API REST para la gestión de la clínica dental Bellidental.",
                contact = @Contact(name = "Equipo Bellidental")
        )
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Ingresa el token JWT obtenido en /api/v1/auth/login"
)
public class OpenApiConfig {
    // La configuración se realiza mediante las anotaciones anteriores.
}

package com.bellidental.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Datos de la clínica que aparecen en documentos impresos (receta, comprobante) y consultas públicas del bot. */
@ConfigurationProperties("application.clinic")
public record ClinicProperties(
        String name,
        String address,
        String phone,
        String zone,
        List<String> hours,
        List<String> paymentMethods,
        String receptionWhatsapp,
        String afterHoursPhone
) {}

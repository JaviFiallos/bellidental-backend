package com.bellidental.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Datos de la clínica que aparecen en documentos impresos (receta, comprobante). */
@ConfigurationProperties("application.clinic")
public record ClinicProperties(String name, String address, String phone) {}

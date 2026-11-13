package com.izalco.transferencias.core;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Conexión al core legado. En dev/qa/prod todos los valores vienen de variables de entorno.
 */
@Validated
@ConfigurationProperties(prefix = "core")
public record CoreProperties(
        @NotBlank String url,
        String username,
        String password,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout) {
}

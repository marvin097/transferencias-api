package com.izalco.transferencias.api;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TransferenciaRequest(
        @NotBlank @Pattern(regexp = "\\d{6,20}", message = "debe tener entre 6 y 20 dígitos")
        String cuentaOrigen,

        @NotBlank @Pattern(regexp = "\\d{6,20}", message = "debe tener entre 6 y 20 dígitos")
        String cuentaDestino,

        @NotBlank @Pattern(regexp = "[A-Z]{3}-\\d{4}", message = "formato esperado: AAA-0000")
        String bancoDestino,

        @NotNull @Positive @Digits(integer = 13, fraction = 2, message = "máximo 13 enteros y 2 decimales")
        BigDecimal monto,

        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "debe ser un código ISO 4217 (ej. USD)")
        String moneda,

        @NotBlank @Size(max = 140)
        String concepto,

        @NotNull
        OffsetDateTime fechaSolicitud) {
}

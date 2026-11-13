package com.izalco.transferencias.api;

import com.izalco.transferencias.dominio.EstadoTransferencia;
import com.izalco.transferencias.dominio.Transferencia;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Respuesta hacia la app. Las cuentas se devuelven enmascaradas (solo últimos 4 dígitos). */
public record TransferenciaResponse(
        String id,
        EstadoTransferencia estado,
        String cuentaOrigen,
        String cuentaDestino,
        String bancoDestino,
        BigDecimal monto,
        String moneda,
        String concepto,
        OffsetDateTime fechaSolicitud,
        String referenciaCore,
        String motivo,
        OffsetDateTime registradaEn) {

    public static TransferenciaResponse desde(Transferencia t) {
        return new TransferenciaResponse(
                t.getId(),
                t.getEstado(),
                enmascarar(t.getCuentaOrigen()),
                enmascarar(t.getCuentaDestino()),
                t.getBancoDestino(),
                t.getMonto(),
                t.getMoneda(),
                t.getConcepto(),
                t.getFechaSolicitud().atOffset(ZoneOffset.UTC),
                t.getReferenciaCore(),
                t.getMotivo(),
                t.getCreadoEn() == null ? null : t.getCreadoEn().atOffset(ZoneOffset.UTC));
    }

    static String enmascarar(String cuenta) {
        int visibles = Math.min(4, cuenta.length());
        return "*".repeat(cuenta.length() - visibles) + cuenta.substring(cuenta.length() - visibles);
    }
}

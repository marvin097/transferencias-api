package com.izalco.transferencias.servicio;

/** Catálogo de errores de negocio que ve la app móvil. */
public enum ErrorNegocio {

    IDEMPOTENCY_KEY_INVALIDA(400,
            "El header X-Idempotency-Key es obligatorio: de 8 a 100 caracteres (letras, números, '-' o '_')."),
    TRANSFERENCIA_NO_ENCONTRADA(404, "La transferencia solicitada no existe."),
    CONFLICTO_IDEMPOTENCIA(409, "La llave de idempotencia ya se usó con una solicitud diferente."),
    SOLICITUD_EN_PROCESO(409, "Ya hay una solicitud en proceso con esta llave de idempotencia. Consulte su estado más tarde."),
    SALDO_INSUFICIENTE(422, "La cuenta de origen no tiene saldo suficiente."),
    CUENTA_BLOQUEADA(422, "La cuenta está bloqueada. Comuníquese con el banco."),
    RESPUESTA_CORE_INVALIDA(502, "El sistema central devolvió una respuesta no reconocida."),
    CORE_NO_DISPONIBLE(503, "El sistema central no está disponible. Intente de nuevo en unos minutos con la misma llave de idempotencia.");

    private final int status;
    private final String mensaje;

    ErrorNegocio(int status, String mensaje) {
        this.status = status;
        this.mensaje = mensaje;
    }

    public int getStatus() {
        return status;
    }

    public String getMensaje() {
        return mensaje;
    }
}

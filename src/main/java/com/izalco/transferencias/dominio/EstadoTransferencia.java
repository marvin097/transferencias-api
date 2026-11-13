package com.izalco.transferencias.dominio;

public enum EstadoTransferencia {
    /** Registrada, enviándose al core. */
    PROCESANDO,
    /** El core respondió 00. */
    APROBADA,
    /** El core respondió con un rechazo de negocio (51, 62 u otro código). */
    RECHAZADA,
    /** El core no contestó a tiempo: NO se sabe si la procesó. No se reenvía automáticamente. */
    EN_VERIFICACION,
    /** Hay certeza de que el core NO la procesó (91 o conexión rechazada). Se puede reintentar con la misma llave. */
    CORE_NO_DISPONIBLE
}

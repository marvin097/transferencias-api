package com.izalco.transferencias.core;

/**
 * El core recibió (o pudo recibir) la operación pero no hubo una respuesta válida: timeout, error HTTP
 * o respuesta ilegible. El resultado es INCIERTO, por lo que NO se debe reenviar automáticamente.
 */
public class CoreSinRespuestaException extends RuntimeException {

    public CoreSinRespuestaException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}

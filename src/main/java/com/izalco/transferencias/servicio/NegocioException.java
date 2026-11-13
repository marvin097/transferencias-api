package com.izalco.transferencias.servicio;

public class NegocioException extends RuntimeException {

    private final ErrorNegocio error;
    private final String transferenciaId;

    public NegocioException(ErrorNegocio error) {
        this(error, null);
    }

    public NegocioException(ErrorNegocio error, String transferenciaId) {
        super(error.getMensaje());
        this.error = error;
        this.transferenciaId = transferenciaId;
    }

    public ErrorNegocio getError() {
        return error;
    }

    public String getTransferenciaId() {
        return transferenciaId;
    }
}

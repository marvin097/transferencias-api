package com.izalco.transferencias.core;

/** Hay certeza de que el core NO procesó la operación (no se pudo conectar). Es seguro reintentar. */
public class CoreNoDisponibleException extends RuntimeException {

    public CoreNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}

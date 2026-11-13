package com.izalco.transferencias.core;

/** Respuesta del core: código (00, 51, 62, 91...) y referencia de la operación. */
public record CoreRespuesta(String codigo, String referencia) {
}

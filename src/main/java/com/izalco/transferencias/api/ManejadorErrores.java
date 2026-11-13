package com.izalco.transferencias.api;

import com.izalco.transferencias.servicio.ErrorNegocio;
import com.izalco.transferencias.servicio.NegocioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;
import java.util.Map;

/** Todas las respuestas de error usan RFC 9457 (application/problem+json) con un campo "codigo" estable. */
@RestControllerAdvice
public class ManejadorErrores extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);
    private static final String SOLICITUD_INVALIDA = "SOLICITUD_INVALIDA";

    @ExceptionHandler(NegocioException.class)
    public ResponseEntity<ProblemDetail> manejarNegocio(NegocioException ex) {
        ErrorNegocio error = ex.getError();
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(error.getStatus()), error.getMensaje());
        problema.setTitle(error.name());
        problema.setProperty("codigo", error.name());
        if (ex.getTransferenciaId() != null) {
            problema.setProperty("transferenciaId", ex.getTransferenciaId());
        }
        ResponseEntity.BodyBuilder respuesta = ResponseEntity.status(error.getStatus());
        if (error == ErrorNegocio.CORE_NO_DISPONIBLE) {
            respuesta.header(HttpHeaders.RETRY_AFTER, "30");
        }
        return respuesta.body(problema);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errores = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> Map.of("campo", f.getField(), "mensaje", String.valueOf(f.getDefaultMessage())))
                .toList();
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, "La solicitud tiene campos inválidos.");
        problema.setTitle(SOLICITUD_INVALIDA);
        problema.setProperty("codigo", SOLICITUD_INVALIDA);
        problema.setProperty("errores", errores);
        return handleExceptionInternal(ex, problema, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status,
                "El cuerpo no es un JSON válido o tiene formatos incorrectos (fechaSolicitud debe incluir zona horaria, ej. 2026-10-01T14:32:10-06:00).");
        problema.setTitle(SOLICITUD_INVALIDA);
        problema.setProperty("codigo", SOLICITUD_INVALIDA);
        return handleExceptionInternal(ex, problema, headers, status, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> manejarInesperado(Exception ex) {
        log.error("Error no controlado", ex);
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(500),
                "Ocurrió un error interno. Intente más tarde.");
        problema.setTitle("ERROR_INTERNO");
        problema.setProperty("codigo", "ERROR_INTERNO");
        return ResponseEntity.status(500).body(problema);
    }
}

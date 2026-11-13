package com.izalco.transferencias.api;

import com.izalco.transferencias.dominio.Transferencia;
import com.izalco.transferencias.servicio.ErrorNegocio;
import com.izalco.transferencias.servicio.NegocioException;
import com.izalco.transferencias.servicio.TransferenciaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/transferencias")
public class TransferenciaController {

    static final String HEADER_IDEMPOTENCIA = "X-Idempotency-Key";
    static final String HEADER_REPETIDA = "Idempotent-Replayed";

    private final TransferenciaService servicio;

    public TransferenciaController(TransferenciaService servicio) {
        this.servicio = servicio;
    }

    @PostMapping
    public ResponseEntity<TransferenciaResponse> registrar(
            @RequestHeader(name = HEADER_IDEMPOTENCIA, required = false) String idempotencyKey,
            @Valid @RequestBody TransferenciaRequest solicitud) {

        TransferenciaService.Resultado resultado = servicio.registrar(idempotencyKey, solicitud);
        Transferencia t = resultado.transferencia();
        URI ubicacion = URI.create("/transferencias/" + t.getId());
        String repetida = String.valueOf(resultado.repetida());

        return switch (t.getEstado()) {
            case APROBADA -> ResponseEntity.created(ubicacion)
                    .header(HEADER_REPETIDA, repetida)
                    .body(TransferenciaResponse.desde(t));
            // Resultado incierto: se acepta y la app consulta el estado con GET. No es un error que deba reintentar.
            case EN_VERIFICACION -> ResponseEntity.accepted()
                    .location(ubicacion)
                    .header(HEADER_REPETIDA, repetida)
                    .body(TransferenciaResponse.desde(t));
            case RECHAZADA -> throw new NegocioException(ErrorNegocio.valueOf(t.getMotivo()), t.getId());
            case CORE_NO_DISPONIBLE -> throw new NegocioException(ErrorNegocio.CORE_NO_DISPONIBLE, t.getId());
            case PROCESANDO -> throw new NegocioException(ErrorNegocio.SOLICITUD_EN_PROCESO, t.getId());
        };
    }

    @GetMapping("/{id}")
    public TransferenciaResponse obtener(@PathVariable("id") String id) {
        return TransferenciaResponse.desde(servicio.obtener(id));
    }
}

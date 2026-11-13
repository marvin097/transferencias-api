package com.izalco.transferencias.servicio;

import com.izalco.transferencias.api.TransferenciaRequest;
import com.izalco.transferencias.core.CoreClient;
import com.izalco.transferencias.core.CoreNoDisponibleException;
import com.izalco.transferencias.core.CoreRespuesta;
import com.izalco.transferencias.core.CoreSinRespuestaException;
import com.izalco.transferencias.dominio.EstadoTransferencia;
import com.izalco.transferencias.dominio.Transferencia;
import com.izalco.transferencias.dominio.TransferenciaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Registra transferencias de forma idempotente:
 * - Llave nueva: se registra y se envía al core una sola vez.
 * - Misma llave + mismo contenido: se devuelve el resultado guardado, sin volver a llamar al core.
 * - Misma llave + contenido distinto: 409.
 * - Único caso que vuelve a llamar al core: el intento anterior quedó en CORE_NO_DISPONIBLE
 *   (hay certeza de que el core no la procesó).
 */
@Service
public class TransferenciaService {

    private static final Logger log = LoggerFactory.getLogger(TransferenciaService.class);
    private static final Pattern FORMATO_LLAVE = Pattern.compile("^[A-Za-z0-9_-]{8,100}$");

    private final TransferenciaRepository repositorio;
    private final CoreClient core;

    public TransferenciaService(TransferenciaRepository repositorio, CoreClient core) {
        this.repositorio = repositorio;
        this.core = core;
    }

    public record Resultado(Transferencia transferencia, boolean repetida) {
    }

    public Resultado registrar(String idempotencyKey, TransferenciaRequest solicitud) {
        if (idempotencyKey == null || !FORMATO_LLAVE.matcher(idempotencyKey).matches()) {
            throw new NegocioException(ErrorNegocio.IDEMPOTENCY_KEY_INVALIDA);
        }
        String huella = huella(solicitud);

        Optional<Transferencia> existente = repositorio.findByIdempotencyKey(idempotencyKey);
        if (existente.isPresent()) {
            return repetir(existente.get(), huella);
        }

        Transferencia nueva = new Transferencia(idempotencyKey, huella,
                solicitud.cuentaOrigen(), solicitud.cuentaDestino(), solicitud.bancoDestino(),
                solicitud.monto(), solicitud.moneda(), solicitud.concepto(),
                solicitud.fechaSolicitud().toInstant());
        try {
            nueva = repositorio.saveAndFlush(nueva);
        } catch (DataIntegrityViolationException e) {
            // Otra solicitud con la misma llave se registró al mismo tiempo.
            throw new NegocioException(ErrorNegocio.SOLICITUD_EN_PROCESO);
        }
        return new Resultado(procesar(nueva), false);
    }

    public Transferencia obtener(String id) {
        return repositorio.findById(id)
                .orElseThrow(() -> new NegocioException(ErrorNegocio.TRANSFERENCIA_NO_ENCONTRADA));
    }

    private Resultado repetir(Transferencia anterior, String huella) {
        if (!anterior.getRequestHash().equals(huella)) {
            throw new NegocioException(ErrorNegocio.CONFLICTO_IDEMPOTENCIA, anterior.getId());
        }
        if (anterior.getEstado() == EstadoTransferencia.PROCESANDO) {
            throw new NegocioException(ErrorNegocio.SOLICITUD_EN_PROCESO, anterior.getId());
        }
        if (anterior.getEstado() == EstadoTransferencia.CORE_NO_DISPONIBLE) {
            anterior.reintentar();
            try {
                anterior = repositorio.saveAndFlush(anterior);
            } catch (OptimisticLockingFailureException e) {
                throw new NegocioException(ErrorNegocio.SOLICITUD_EN_PROCESO, anterior.getId());
            }
            return new Resultado(procesar(anterior), false);
        }
        log.info("Solicitud repetida para transferencia {}: se devuelve el resultado guardado", anterior.getId());
        return new Resultado(anterior, true);
    }

    private Transferencia procesar(Transferencia t) {
        try {
            CoreRespuesta respuesta = core.enviar(t);
            switch (respuesta.codigo()) {
                case "00" -> t.aprobar(respuesta.codigo(), respuesta.referencia());
                case "51" -> t.rechazar(respuesta.codigo(), ErrorNegocio.SALDO_INSUFICIENTE.name());
                case "62" -> t.rechazar(respuesta.codigo(), ErrorNegocio.CUENTA_BLOQUEADA.name());
                case "91" -> t.marcarCoreNoDisponible(respuesta.codigo());
                default -> {
                    log.warn("Código de core no reconocido '{}' en transferencia {}", respuesta.codigo(), t.getId());
                    t.rechazar(respuesta.codigo(), ErrorNegocio.RESPUESTA_CORE_INVALIDA.name());
                }
            }
        } catch (CoreNoDisponibleException e) {
            log.warn("Core no disponible para transferencia {}: {}", t.getId(), e.getMessage());
            t.marcarCoreNoDisponible(null);
        } catch (CoreSinRespuestaException e) {
            log.error("Resultado incierto en transferencia {}, queda EN_VERIFICACION: {}", t.getId(), e.getMessage());
            t.marcarEnVerificacion("CORE_SIN_RESPUESTA");
        }
        return repositorio.save(t);
    }

    /** SHA-256 de los campos de negocio, para detectar si una llave se reutiliza con otro contenido. */
    static String huella(TransferenciaRequest s) {
        String canonica = String.join("|",
                s.cuentaOrigen(), s.cuentaDestino(), s.bancoDestino(),
                s.monto().stripTrailingZeros().toPlainString(), s.moneda(), s.concepto(),
                s.fechaSolicitud().toInstant().toString());
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonica.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}

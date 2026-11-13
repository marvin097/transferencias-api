package com.izalco.transferencias.api;

import com.izalco.transferencias.soporte.CoreSimulado;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Prueba de punta a punta: HTTP -> API -> base de datos H2 -> core simulado por HTTP. */
@SpringBootTest
@AutoConfigureMockMvc
class TransferenciaApiTest {

    private static final CoreSimulado CORE = new CoreSimulado();

    private static final String SOLICITUD = """
            {
              "cuentaOrigen": "100200300",
              "cuentaDestino": "900800700",
              "bancoDestino": "BIZ-0042",
              "monto": 150.75,
              "moneda": "USD",
              "concepto": "Pago de alquiler",
              "fechaSolicitud": "2026-10-01T14:32:10-06:00"
            }
            """;

    @Autowired
    private MockMvc mvc;

    @DynamicPropertySource
    static void configurarCore(DynamicPropertyRegistry registro) {
        registro.add("core.url", CORE::url);
        registro.add("core.read-timeout", () -> "500ms");
    }

    @AfterAll
    static void apagar() {
        CORE.close();
    }

    @BeforeEach
    void reiniciar() {
        CORE.reiniciar();
    }

    private ResultActions registrar(String llave, String cuerpo) throws Exception {
        var solicitud = post("/transferencias").contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        if (llave != null) {
            solicitud.header("X-Idempotency-Key", llave);
        }
        return mvc.perform(solicitud);
    }

    private static String llave() {
        return UUID.randomUUID().toString();
    }

    @Test
    void transferenciaAprobadaDevuelve201YSeEnviaAlCoreEnUtc() throws Exception {
        registrar(llave(), SOLICITUD)
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andExpect(jsonPath("$.estado").value("APROBADA"))
                .andExpect(jsonPath("$.referenciaCore").value("REF-0001"))
                .andExpect(jsonPath("$.cuentaOrigen").value("*****0300"));

        assertThat(CORE.solicitudes()).singleElement().asString()
                .contains("<Glosa>PAGO DE ALQUILER</Glosa>")
                .contains("<FechaHora>20261001203210</FechaHora>");
    }

    @Test
    void reintentoConLaMismaLlaveNoDuplicaLaTransferencia() throws Exception {
        String llave = llave();
        String id = registrar(llave, SOLICITUD).andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");

        registrar(llave, SOLICITUD)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", id))
                .andExpect(header().string("Idempotent-Replayed", "true"));

        assertThat(CORE.solicitudes()).hasSize(1);
    }

    @Test
    void mismaLlaveConOtroContenidoDevuelve409() throws Exception {
        String llave = llave();
        registrar(llave, SOLICITUD).andExpect(status().isCreated());

        registrar(llave, SOLICITUD.replace("150.75", "999.99"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO_IDEMPOTENCIA"));

        assertThat(CORE.solicitudes()).hasSize(1);
    }

    @Test
    void sinLlaveDeIdempotenciaDevuelve400() throws Exception {
        registrar(null, SOLICITUD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("IDEMPOTENCY_KEY_INVALIDA"));
        assertThat(CORE.solicitudes()).isEmpty();
    }

    @Test
    void camposInvalidosDevuelven400ConDetalle() throws Exception {
        registrar(llave(), SOLICITUD.replace("\"USD\"", "\"dolares\"").replace("150.75", "-5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"))
                .andExpect(jsonPath("$.errores.length()").value(2));
    }

    @Test
    void fechaSinZonaHorariaDevuelve400() throws Exception {
        registrar(llave(), SOLICITUD.replace("2026-10-01T14:32:10-06:00", "2026-10-01T14:32:10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
    }

    @Test
    void saldoInsuficienteDevuelve422() throws Exception {
        CORE.responderCodigo("51");
        registrar(llave(), SOLICITUD)
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.codigo").value("SALDO_INSUFICIENTE"))
                .andExpect(jsonPath("$.transferenciaId").exists());
    }

    @Test
    void cuentaBloqueadaDevuelve422() throws Exception {
        CORE.responderCodigo("62");
        registrar(llave(), SOLICITUD)
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.codigo").value("CUENTA_BLOQUEADA"));
    }

    @Test
    void codigoDesconocidoDelCoreDevuelve502() throws Exception {
        CORE.responderCodigo("99");
        registrar(llave(), SOLICITUD)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.codigo").value("RESPUESTA_CORE_INVALIDA"));
    }

    @Test
    void coreNoDisponibleDevuelve503YPermiteReintentarConLaMismaLlave() throws Exception {
        String llave = llave();
        CORE.responderCodigo("91");
        registrar(llave, SOLICITUD)
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.codigo").value("CORE_NO_DISPONIBLE"));

        CORE.responderCodigo("00");
        registrar(llave, SOLICITUD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("APROBADA"));

        assertThat(CORE.solicitudes()).hasSize(2);
    }

    @Test
    void coreSinRespuestaQuedaEnVerificacionYNoSeReenvia() throws Exception {
        // Escenario del INC-2026-118: el core tarda más que el timeout.
        String llave = llave();
        CORE.demorar(Duration.ofMillis(1500));
        String ubicacion = registrar(llave, SOLICITUD)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.estado").value("EN_VERIFICACION"))
                .andReturn().getResponse().getHeader("Location");

        // La app reintenta: NO se vuelve a enviar al core (evita la transferencia duplicada).
        registrar(llave, SOLICITUD)
                .andExpect(status().isAccepted())
                .andExpect(header().string("Idempotent-Replayed", "true"));
        assertThat(CORE.solicitudes()).hasSize(1);

        mvc.perform(get(ubicacion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_VERIFICACION"));
    }

    @Test
    void consultaTransferenciaExistente() throws Exception {
        String ubicacion = registrar(llave(), SOLICITUD).andReturn().getResponse().getHeader("Location");

        mvc.perform(get(ubicacion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADA"))
                .andExpect(jsonPath("$.monto").value(150.75))
                .andExpect(jsonPath("$.fechaSolicitud").value("2026-10-01T20:32:10Z"));
    }

    @Test
    void consultaTransferenciaInexistenteDevuelve404() throws Exception {
        mvc.perform(get("/transferencias/no-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("TRANSFERENCIA_NO_ENCONTRADA"));
    }
}

package com.izalco.transferencias.core;

import com.izalco.transferencias.dominio.Transferencia;
import com.izalco.transferencias.soporte.CoreSimulado;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoreClientTest {

    private static final CoreSimulado CORE = new CoreSimulado();

    private final Transferencia transferencia = new Transferencia("llave-123456", "hash", "100200300", "900800700",
            "BIZ-0042", new BigDecimal("150.75"), "USD", "Pago de alquiler",
            OffsetDateTime.parse("2026-10-01T14:32:10-06:00").toInstant());

    @AfterAll
    static void apagar() {
        CORE.close();
    }

    @BeforeEach
    void reiniciar() {
        CORE.reiniciar();
    }

    private static CoreClient cliente(String url, String usuario, String password) {
        return new CoreClient(new CoreProperties(url, usuario, password, Duration.ofMillis(500), Duration.ofMillis(500)));
    }

    @Test
    void enviaXmlYLeeLaRespuesta() {
        CORE.responderCodigo("00");

        CoreRespuesta respuesta = cliente(CORE.url(), "", "").enviar(transferencia);

        assertThat(respuesta.codigo()).isEqualTo("00");
        assertThat(CORE.solicitudes()).singleElement().asString()
                .contains("<FechaHora>20261001203210</FechaHora>");
        assertThat(CORE.autorizaciones()).singleElement().isEqualTo("");
    }

    @Test
    void enviaCredencialesDeServicioCuandoEstanConfiguradas() {
        cliente(CORE.url(), "svc_test", "secreto-de-prueba").enviar(transferencia);

        assertThat(CORE.autorizaciones()).singleElement().asString().startsWith("Basic ");
    }

    @Test
    void coreCaidoConexionRechazadaEsNoDisponible() throws IOException {
        int puertoCerrado;
        try (ServerSocket socket = new ServerSocket(0)) {
            puertoCerrado = socket.getLocalPort();
        }
        CoreClient cliente = cliente("http://localhost:" + puertoCerrado + "/core/transfer", null, null);

        assertThatThrownBy(() -> cliente.enviar(transferencia))
                .isInstanceOf(CoreNoDisponibleException.class);
    }

    @Test
    void coreLentoEsResultadoIncierto() {
        CORE.demorar(Duration.ofMillis(1500));

        assertThatThrownBy(() -> cliente(CORE.url(), null, null).enviar(transferencia))
                .isInstanceOf(CoreSinRespuestaException.class);
    }

    @Test
    void error500DelCoreEsResultadoIncierto() {
        CORE.responder(500, "error");

        assertThatThrownBy(() -> cliente(CORE.url(), null, null).enviar(transferencia))
                .isInstanceOf(CoreSinRespuestaException.class);
    }

    @Test
    void error503DelCoreEsNoDisponible() {
        CORE.responder(503, "mantenimiento");

        assertThatThrownBy(() -> cliente(CORE.url(), null, null).enviar(transferencia))
                .isInstanceOf(CoreNoDisponibleException.class);
    }
}

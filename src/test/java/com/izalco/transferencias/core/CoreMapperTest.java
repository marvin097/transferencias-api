package com.izalco.transferencias.core;

import com.izalco.transferencias.dominio.Transferencia;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoreMapperTest {

    @Test
    void convierteHoraLocalAUtcConFormatoDelCore() {
        // Caso del enunciado y del incidente: 14:32 en El Salvador (UTC-6) = 20:32 UTC.
        var fecha = OffsetDateTime.parse("2026-10-01T14:32:10-06:00").toInstant();
        assertThat(CoreMapper.formatearFecha(fecha)).isEqualTo("20261001203210");
    }

    @Test
    void glosaEnMayusculasSinTildesYMaximo30Caracteres() {
        assertThat(CoreMapper.normalizarGlosa("Pago de alquiler")).isEqualTo("PAGO DE ALQUILER");
        assertThat(CoreMapper.normalizarGlosa("  Pago   de energía  ")).isEqualTo("PAGO DE ENERGIA");
        assertThat(CoreMapper.normalizarGlosa("Pago de la cuota número doce del préstamo"))
                .hasSize(CoreMapper.MAX_GLOSA)
                .isEqualTo("PAGO DE LA CUOTA NUMERO DOCE D");
    }

    @Test
    void generaElXmlEsperadoPorElCore() {
        var t = new Transferencia("llave-123456", "hash", "100200300", "900800700", "BIZ-0042",
                new BigDecimal("150.75"), "USD", "Pago de alquiler",
                OffsetDateTime.parse("2026-10-01T14:32:10-06:00").toInstant());

        assertThat(CoreMapper.aXml(t)).isEqualTo(
                "<TransferenciaRequest>"
                        + "<Origen>100200300</Origen>"
                        + "<Destino>900800700</Destino>"
                        + "<CodigoBanco>BIZ-0042</CodigoBanco>"
                        + "<Importe moneda=\"USD\">150.75</Importe>"
                        + "<Glosa>PAGO DE ALQUILER</Glosa>"
                        + "<FechaHora>20261001203210</FechaHora>"
                        + "</TransferenciaRequest>");
    }

    @Test
    void importeSiempreConDosDecimales() {
        var t = new Transferencia("llave-123456", "hash", "100200300", "900800700", "BIZ-0042",
                new BigDecimal("150"), "USD", "Pago", OffsetDateTime.parse("2026-10-01T14:32:10-06:00").toInstant());
        assertThat(CoreMapper.aXml(t)).contains("<Importe moneda=\"USD\">150.00</Importe>");
    }

    @Test
    void leeLaRespuestaDelCore() {
        var respuesta = CoreMapper.leerRespuesta(
                "<TransferenciaResponse><Codigo>00</Codigo><Referencia>ABC123</Referencia></TransferenciaResponse>");
        assertThat(respuesta.codigo()).isEqualTo("00");
        assertThat(respuesta.referencia()).isEqualTo("ABC123");
    }

    @Test
    void respuestaVaciaSinCodigoOMalFormadaEsResultadoIncierto() {
        assertThatThrownBy(() -> CoreMapper.leerRespuesta(""))
                .isInstanceOf(CoreSinRespuestaException.class);
        assertThatThrownBy(() -> CoreMapper.leerRespuesta("<TransferenciaResponse></TransferenciaResponse>"))
                .isInstanceOf(CoreSinRespuestaException.class);
        assertThatThrownBy(() -> CoreMapper.leerRespuesta("<TransferenciaResponse><Codigo>00"))
                .isInstanceOf(CoreSinRespuestaException.class);
    }

    @Test
    void rechazaXmlConEntidadesExternas() {
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE r [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>"
                + "<TransferenciaResponse><Codigo>&x;</Codigo></TransferenciaResponse>";
        assertThatThrownBy(() -> CoreMapper.leerRespuesta(xxe))
                .isInstanceOf(CoreSinRespuestaException.class);
    }
}

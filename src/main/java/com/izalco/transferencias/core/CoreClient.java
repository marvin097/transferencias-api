package com.izalco.transferencias.core;

import com.izalco.transferencias.dominio.Transferencia;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

/**
 * Cliente HTTP del core legado. No reintenta: un reintento ciego sobre una transferencia
 * puede duplicarla (ver INC-2026-118). Los reintentos seguros los controla la idempotencia.
 */
@Component
public class CoreClient {

    private static final MediaType XML_UTF8 = new MediaType(MediaType.APPLICATION_XML, StandardCharsets.UTF_8);

    private final RestClient restClient;
    private final CoreProperties propiedades;

    public CoreClient(CoreProperties propiedades) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(propiedades.connectTimeout());
        fabrica.setReadTimeout(propiedades.readTimeout());

        RestClient.Builder builder = RestClient.builder().requestFactory(fabrica);
        if (StringUtils.hasText(propiedades.username())) {
            String password = propiedades.password() == null ? "" : propiedades.password();
            builder.defaultHeaders(h -> h.setBasicAuth(propiedades.username(), password));
        }
        this.restClient = builder.build();
        this.propiedades = propiedades;
    }

    public CoreRespuesta enviar(Transferencia transferencia) {
        String xml = CoreMapper.aXml(transferencia);
        String respuesta;
        try {
            respuesta = restClient.post()
                    .uri(propiedades.url())
                    .contentType(XML_UTF8)
                    .accept(MediaType.APPLICATION_XML)
                    .body(xml)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 503) {
                throw new CoreNoDisponibleException("El core respondió 503 Service Unavailable", e);
            }
            throw new CoreSinRespuestaException("El core respondió HTTP " + e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            // Incluye ResourceAccessException y los errores de E/S al leer la respuesta
            // (el timeout de lectura ocurre al leer el status, no al conectar).
            if (noSeEnvio(e)) {
                throw new CoreNoDisponibleException("No fue posible conectar con el core", e);
            }
            throw new CoreSinRespuestaException("El core no respondió a tiempo o la respuesta no se pudo leer", e);
        }
        return CoreMapper.leerRespuesta(respuesta);
    }

    /** Solo hay certeza de que la solicitud no llegó si la conexión fue rechazada o el host no existe. */
    private static boolean noSeEnvio(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof ConnectException || t instanceof UnknownHostException) {
                return true;
            }
        }
        return false;
    }
}

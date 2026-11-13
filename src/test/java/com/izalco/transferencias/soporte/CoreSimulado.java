package com.izalco.transferencias.soporte;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

/** Core legado falso sobre HTTP real, para probar respuestas, demoras y caídas. */
public final class CoreSimulado implements AutoCloseable {

    private final HttpServer servidor;
    private final List<String> solicitudes = new CopyOnWriteArrayList<>();
    private final List<String> autorizaciones = new CopyOnWriteArrayList<>();
    private volatile int status = 200;
    private volatile String cuerpo = respuesta("00", "REF-0001");
    private volatile Duration demora = Duration.ZERO;

    public CoreSimulado() {
        try {
            servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        servidor.setExecutor(Executors.newCachedThreadPool());
        servidor.createContext("/core/transfer", intercambio -> {
            solicitudes.add(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String auth = intercambio.getRequestHeaders().getFirst("Authorization");
            autorizaciones.add(auth == null ? "" : auth);
            try {
                Thread.sleep(demora.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/xml");
            try {
                intercambio.sendResponseHeaders(status, bytes.length);
                try (OutputStream salida = intercambio.getResponseBody()) {
                    salida.write(bytes);
                }
            } catch (IOException e) {
                // el cliente cerró la conexión (timeout): se ignora
            }
        });
        servidor.start();
    }

    public static String respuesta(String codigo, String referencia) {
        return "<TransferenciaResponse><Codigo>" + codigo + "</Codigo><Referencia>" + referencia
                + "</Referencia></TransferenciaResponse>";
    }

    public String url() {
        return "http://localhost:" + servidor.getAddress().getPort() + "/core/transfer";
    }

    public void responder(int status, String cuerpo) {
        this.status = status;
        this.cuerpo = cuerpo;
    }

    public void responderCodigo(String codigo) {
        responder(200, respuesta(codigo, "REF-" + codigo));
    }

    public void demorar(Duration demora) {
        this.demora = demora;
    }

    public void reiniciar() {
        solicitudes.clear();
        autorizaciones.clear();
        status = 200;
        cuerpo = respuesta("00", "REF-0001");
        demora = Duration.ZERO;
    }

    public List<String> solicitudes() {
        return solicitudes;
    }

    public List<String> autorizaciones() {
        return autorizaciones;
    }

    @Override
    public void close() {
        servidor.stop(0);
    }
}

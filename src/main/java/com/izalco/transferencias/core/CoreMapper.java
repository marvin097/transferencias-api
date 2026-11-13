package com.izalco.transferencias.core;

import com.izalco.transferencias.dominio.Transferencia;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.XMLOutputFactory;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Transforma una transferencia al formato XML del core legado y lee su respuesta.
 * Reglas del core: fecha en UTC con formato yyyyMMddHHmmss y glosa en mayúsculas, máximo 30 caracteres.
 */
public final class CoreMapper {

    static final int MAX_GLOSA = 30;
    private static final DateTimeFormatter FORMATO_FECHA_CORE =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private CoreMapper() {
    }

    /** Convierte el instante a UTC. Ej.: 2026-10-01T14:32:10-06:00 -> 20261001203210. */
    public static String formatearFecha(Instant instante) {
        return FORMATO_FECHA_CORE.format(instante);
    }

    /**
     * Mayúsculas, sin tildes (el core legado no garantiza soporte de acentos), espacios normalizados
     * y recortada a 30 caracteres.
     */
    public static String normalizarGlosa(String concepto) {
        String sinTildes = Normalizer.normalize(concepto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String limpia = sinTildes.replaceAll("\\s+", " ").trim().toUpperCase(Locale.ROOT);
        return limpia.length() <= MAX_GLOSA ? limpia : limpia.substring(0, MAX_GLOSA).trim();
    }

    public static String aXml(Transferencia t) {
        try {
            StringWriter salida = new StringWriter();
            XMLStreamWriter xml = XMLOutputFactory.newFactory().createXMLStreamWriter(salida);
            xml.writeStartElement("TransferenciaRequest");
            elemento(xml, "Origen", t.getCuentaOrigen());
            elemento(xml, "Destino", t.getCuentaDestino());
            elemento(xml, "CodigoBanco", t.getBancoDestino());
            xml.writeStartElement("Importe");
            xml.writeAttribute("moneda", t.getMoneda());
            xml.writeCharacters(t.getMonto().setScale(2, RoundingMode.UNNECESSARY).toPlainString());
            xml.writeEndElement();
            elemento(xml, "Glosa", normalizarGlosa(t.getConcepto()));
            elemento(xml, "FechaHora", formatearFecha(t.getFechaSolicitud()));
            xml.writeEndElement();
            xml.flush();
            xml.close();
            return salida.toString();
        } catch (XMLStreamException e) {
            throw new IllegalStateException("No se pudo generar el XML para el core", e);
        }
    }

    /** Lee &lt;TransferenciaResponse&gt;&lt;Codigo&gt;..&lt;/Codigo&gt;&lt;Referencia&gt;..&lt;/Referencia&gt;. */
    public static CoreRespuesta leerRespuesta(String xml) {
        if (xml == null || xml.isBlank()) {
            throw new CoreSinRespuestaException("El core devolvió una respuesta vacía", null);
        }
        try {
            XMLInputFactory fabrica = XMLInputFactory.newFactory();
            // Protección contra XXE: sin DTD ni entidades externas.
            fabrica.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            fabrica.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            XMLStreamReader lector = fabrica.createXMLStreamReader(new StringReader(xml));
            String codigo = null;
            String referencia = null;
            while (lector.hasNext()) {
                if (lector.next() == XMLStreamConstants.START_ELEMENT) {
                    switch (lector.getLocalName()) {
                        case "Codigo" -> codigo = lector.getElementText().trim();
                        case "Referencia" -> referencia = lector.getElementText().trim();
                        default -> { /* se ignoran otros elementos */ }
                    }
                }
            }
            lector.close();
            if (codigo == null || codigo.isEmpty()) {
                throw new CoreSinRespuestaException("La respuesta del core no trae <Codigo>", null);
            }
            return new CoreRespuesta(codigo, referencia);
        } catch (XMLStreamException e) {
            throw new CoreSinRespuestaException("La respuesta del core no es un XML válido", e);
        }
    }

    private static void elemento(XMLStreamWriter xml, String nombre, String valor) throws XMLStreamException {
        xml.writeStartElement(nombre);
        xml.writeCharacters(valor);
        xml.writeEndElement();
    }
}

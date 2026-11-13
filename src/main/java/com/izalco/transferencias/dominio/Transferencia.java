package com.izalco.transferencias.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transferencias")
public class Transferencia {

    @Id
    @Column(length = 36)
    private String id;

    @Version
    private Long version;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "cuenta_origen", nullable = false, length = 20)
    private String cuentaOrigen;

    @Column(name = "cuenta_destino", nullable = false, length = 20)
    private String cuentaDestino;

    @Column(name = "banco_destino", nullable = false, length = 20)
    private String bancoDestino;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false, length = 3)
    private String moneda;

    @Column(nullable = false, length = 140)
    private String concepto;

    @Column(name = "fecha_solicitud", nullable = false)
    private Instant fechaSolicitud;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoTransferencia estado;

    @Column(name = "codigo_core", length = 5)
    private String codigoCore;

    @Column(name = "referencia_core", length = 50)
    private String referenciaCore;

    @Column(length = 40)
    private String motivo;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    protected Transferencia() {
        // requerido por JPA
    }

    public Transferencia(String idempotencyKey, String requestHash, String cuentaOrigen, String cuentaDestino,
                         String bancoDestino, BigDecimal monto, String moneda, String concepto, Instant fechaSolicitud) {
        this.id = UUID.randomUUID().toString();
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.cuentaOrigen = cuentaOrigen;
        this.cuentaDestino = cuentaDestino;
        this.bancoDestino = bancoDestino;
        this.monto = monto;
        this.moneda = moneda;
        this.concepto = concepto;
        this.fechaSolicitud = fechaSolicitud;
        this.estado = EstadoTransferencia.PROCESANDO;
    }

    public void aprobar(String codigo, String referencia) {
        this.estado = EstadoTransferencia.APROBADA;
        this.codigoCore = codigo;
        this.referenciaCore = referencia;
        this.motivo = null;
    }

    public void rechazar(String codigo, String motivo) {
        this.estado = EstadoTransferencia.RECHAZADA;
        this.codigoCore = codigo;
        this.motivo = motivo;
    }

    public void marcarEnVerificacion(String motivo) {
        this.estado = EstadoTransferencia.EN_VERIFICACION;
        this.motivo = motivo;
    }

    public void marcarCoreNoDisponible(String codigo) {
        this.estado = EstadoTransferencia.CORE_NO_DISPONIBLE;
        this.codigoCore = codigo;
        this.motivo = "CORE_NO_DISPONIBLE";
    }

    public void reintentar() {
        if (estado != EstadoTransferencia.CORE_NO_DISPONIBLE) {
            throw new IllegalStateException("Solo se reintenta una transferencia que el core no procesó");
        }
        this.estado = EstadoTransferencia.PROCESANDO;
        this.codigoCore = null;
        this.motivo = null;
    }

    @PrePersist
    void alCrear() {
        Instant ahora = Instant.now();
        this.creadoEn = ahora;
        this.actualizadoEn = ahora;
    }

    @PreUpdate
    void alActualizar() {
        this.actualizadoEn = Instant.now();
    }

    public String getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public String getCuentaOrigen() { return cuentaOrigen; }
    public String getCuentaDestino() { return cuentaDestino; }
    public String getBancoDestino() { return bancoDestino; }
    public BigDecimal getMonto() { return monto; }
    public String getMoneda() { return moneda; }
    public String getConcepto() { return concepto; }
    public Instant getFechaSolicitud() { return fechaSolicitud; }
    public EstadoTransferencia getEstado() { return estado; }
    public String getCodigoCore() { return codigoCore; }
    public String getReferenciaCore() { return referenciaCore; }
    public String getMotivo() { return motivo; }
    public Instant getCreadoEn() { return creadoEn; }
}

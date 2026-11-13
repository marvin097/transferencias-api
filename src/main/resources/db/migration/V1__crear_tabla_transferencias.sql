-- Compatible con SQL Server y con H2 en modo MSSQLServer (pruebas).
CREATE TABLE transferencias (
    id               VARCHAR(36)    NOT NULL,
    version          BIGINT         NOT NULL,
    idempotency_key  VARCHAR(100)   NOT NULL,
    request_hash     VARCHAR(64)    NOT NULL,
    cuenta_origen    VARCHAR(20)    NOT NULL,
    cuenta_destino   VARCHAR(20)    NOT NULL,
    banco_destino    VARCHAR(20)    NOT NULL,
    monto            DECIMAL(15,2)  NOT NULL,
    moneda           VARCHAR(3)     NOT NULL,
    concepto         NVARCHAR(140)  NOT NULL,
    fecha_solicitud  DATETIME2      NOT NULL,
    estado           VARCHAR(30)    NOT NULL,
    codigo_core      VARCHAR(5),
    referencia_core  VARCHAR(50),
    motivo           VARCHAR(40),
    creado_en        DATETIME2      NOT NULL,
    actualizado_en   DATETIME2      NOT NULL,
    CONSTRAINT pk_transferencias PRIMARY KEY (id),
    CONSTRAINT uk_transferencias_idempotency UNIQUE (idempotency_key)
);

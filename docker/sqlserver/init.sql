-- Crea la base de datos y un login propio para la API (la API nunca usa "sa").
-- La contraseña llega como variable de sqlcmd: $(APP_DB_PASSWORD).
IF DB_ID('transferencias') IS NULL
    CREATE DATABASE transferencias;
GO

IF SUSER_ID('app_transferencias') IS NULL
    CREATE LOGIN app_transferencias WITH PASSWORD = '$(APP_DB_PASSWORD)', CHECK_POLICY = ON;
GO

USE transferencias;
GO

IF USER_ID('app_transferencias') IS NULL
    CREATE USER app_transferencias FOR LOGIN app_transferencias;
GO

-- db_owner porque Flyway crea y modifica tablas. En prod, las migraciones correrían
-- con una identidad separada y la API solo tendría permisos de lectura/escritura.
ALTER ROLE db_owner ADD MEMBER app_transferencias;
GO

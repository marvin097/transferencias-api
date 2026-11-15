#!/usr/bin/env bash
# Levanta TODO el ambiente local con un solo comando:
# API + API Gateway (Kong) + SQL Server + core simulado.
#
# Uso: ./scripts/levantar-local.sh
# Requisitos: Docker (Docker Desktop abierto), Java 21, bash, curl y openssl.
set -euo pipefail
cd "$(dirname "$0")/.."

docker info > /dev/null 2>&1 || { echo "Docker no está corriendo. Abra Docker Desktop y vuelva a intentar."; exit 1; }

# 1) Archivo .env: si no existe (o le falta alguna variable) se generan claves aleatorias locales.
touch .env
[ -s .env ] && [ -n "$(tail -c1 .env)" ] && echo >> .env   # asegura salto de línea final
agregar() { grep -q "^$1=" .env || { echo "$1=$2" >> .env; echo "   + $1 generada"; }; }
echo "-> Verificando .env (las claves reales nunca se suben a git)"
agregar MSSQL_SA_PASSWORD   "Sa-$(openssl rand -hex 10)-Aa1!"
agregar APP_DB_PASSWORD     "App-$(openssl rand -hex 10)-Aa1!"
agregar CORE_PASSWORD       "core-$(openssl rand -hex 12)"
agregar CLIENT_ID_APP_MOVIL "app-movil-$(openssl rand -hex 8)"

# 2) Compilar y probar (falla si la cobertura es menor a 80 %).
echo "-> Compilando y ejecutando pruebas (1-2 minutos)..."
if ! ./mvnw -B clean verify > build.log 2>&1; then
  grep -E "Tests run:|ERROR|FAIL" build.log | tail -n 20
  echo "La compilación falló. Detalle completo en build.log"; exit 1
fi
grep -E "Tests run:" build.log | tail -n 1

# 3) Contenedores.
echo "-> Levantando contenedores (la primera vez descarga SQL Server, ~1.5 GB)..."
docker compose up -d --build

echo "-> Esperando a que la API responda a través del gateway..."
for _ in $(seq 1 60); do
  curl -sf localhost:8000/actuator/health > /dev/null && break
  sleep 5
done
curl -sf localhost:8000/actuator/health > /dev/null || {
  docker compose ps -a
  echo "La API no respondió a tiempo. Revise: docker compose logs api"; exit 1; }

CLIENT_ID=$(grep '^CLIENT_ID_APP_MOVIL=' .env | cut -d= -f2-)
cat <<FIN

  Ambiente local listo
  ----------------------------------------------------------------
  Swagger UI : http://localhost:8000/swagger-ui.html
  client_id  : $CLIENT_ID   (botón "Authorize" en Swagger)
  Salud      : http://localhost:8000/actuator/health
  Base datos : localhost,1433 (usuario app_transferencias, clave en .env)

  Apagar     : docker compose down      (conserva los datos)
  Desde cero : docker compose down -v   (borra la base de datos)
FIN

# Imagen de ejecución de la API.
# El JAR lo construye y prueba Maven ANTES (./mvnw verify), tanto en local como en el pipeline.
# Así la imagen contiene exactamente el artefacto que pasó las pruebas y la cobertura,
# y esa misma imagen es la que se promueve a dev, qa y prod.
FROM eclipse-temurin:21-jre

ARG VERSION=dev
ARG REVISION=local
LABEL org.opencontainers.image.title="transferencias-api" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${REVISION}"

# Usuario sin privilegios: la API nunca corre como root.
RUN groupadd --system app && useradd --system --gid app --no-create-home app

WORKDIR /app
COPY target/transferencias-api-*.jar app.jar

USER app
ENV TZ=UTC \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

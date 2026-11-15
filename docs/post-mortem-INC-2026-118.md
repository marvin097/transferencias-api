# Post-mortem INC-2026-118: transferencias fallidas y duplicadas tras desplegar la versión 2.3.0

**Severidad:** Alta · **Ventana:** viernes de 4:40 p.m. a 5:20 p.m. · **Servicio:** API de transferencias interbancarias

## 1. Hallazgos en la evidencia

| # | Fuente | Hallazgo |
|---|---|---|
| H1 | Pipeline | Cobertura de 61 % con el umbral **desactivado**. |
| H2 | Pipeline | Despliegue con el usuario personal de un proveedor (`jlopez.contractor`). |
| H3 | Pipeline | Artefacto `2.3.0-SNAPSHOT` (no inmutable) y runtime 4.3.0 no soportado, desplegado igual con un *fallback*. |
| H4 | Pipeline | Aprobación **omitida** por ser rama `hotfix/*`, un viernes por la tarde. |
| H5 | config-prod | `core.url` apunta al core de **QA**, y usuario y contraseña en **texto plano** en el repositorio. |
| H6 | config-prod | `timeout: 5000` ms, menor que el p95 del core (9.8 s): se cortaban operaciones que el core sí procesaba. |
| H7 | config-prod | `apiId` igual al de QA (prod con las políticas de QA); `idempotency: false`; `replicas: 1`. |
| H8 | Monitoreo | 14 % de respuestas 504 y la app reintenta ante un 504: 37 clientes con transferencias **duplicadas**. |
| H9 | Monitoreo | Hora con el desfase **invertido**: 14:32 (UTC-6) debía enviarse como 20:32 UTC y llegó como 08:32. |

## 2. Causa raíz

El proceso de entrega permitía **saltarse todos los controles** (cobertura, aprobación, identidad de servicio, versión de release), y así llegó a producción una configuración con defectos que se combinaron: un timeout menor que la latencia real del core (H6) produjo 504 en operaciones que sí se procesaban; con la idempotencia apagada (H7) y una app que reintenta (H8), esos 504 se convirtieron en **transferencias duplicadas**. A eso se sumó una conversión de zona horaria incorrecta (H9). Ninguna barrera que debía detenerlo era obligatoria.

## 3. Impacto

- **Clientes:** 37 clientes con doble débito, y cerca de 1 de cada 7 operaciones fallidas durante 40 minutos.
- **Banco:** operaciones de producción registradas en el core de **QA** y con hora incorrecta, lo que obliga a conciliar toda la ventana; una credencial expuesta; riesgo reputacional y regulatorio; y un hallazgo de Auditoría que **se repite** (despliegue con usuario personal).

## 4. Corrección inmediata

1. Revertir a la última versión estable, por el pipeline y con aprobación, y congelar los despliegues de esta API.
2. Corregir la configuración de prod: URL del core de producción, `apiId` propio, idempotencia activa, timeout mayor que el p95 del core y al menos 2 réplicas.
3. **Rotar** la contraseña expuesta, moverla a un gestor de secretos y revocar el acceso personal del proveedor.
4. Reversar los 37 débitos duplicados, conciliar con el core las operaciones de 4:40 a 5:20 p.m., corregir sus horas e informar a los clientes.

## 5. Controles para que no vuelva a ocurrir (implementados en este repositorio)

| Control | Cubre |
|---|---|
| Cobertura mínima de 80 % fija en el `pom.xml`; cambios al pipeline revisados vía `CODEOWNERS`. | H1 |
| Despliegue solo por el pipeline, con `GITHUB_TOKEN` efímero (identidad de servicio). | H2 |
| Versión de release y la **misma imagen por digest `sha256`** en dev, qa y prod; runtime fijado en la imagen. | H3 |
| Aprobación obligatoria en prod **para toda rama**, solo desde `main` y sin auto-aprobación. | H4 |
| Configuración y secretos por ambiente, **sin valores por defecto**: si falta uno, la API no arranca. | H5 |
| Gateway 15 s > API→core 12 s > p95 del core; si no hay respuesta, `202 EN_VERIFICACION` sin reenvío. | H6 |
| `X-Idempotency-Key` obligatoria (huella SHA-256 y llave única en BD); políticas por ambiente en `kong/kong.yml`. | H7, H8 |
| Prueba automática de UTC (`14:32:10-06:00` → `20261001203210`) y pruebas de humo tras cada despliegue. | H9 |

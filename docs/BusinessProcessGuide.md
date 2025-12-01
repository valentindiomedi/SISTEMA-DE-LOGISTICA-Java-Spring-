Guía de Proceso de Negocio — Flujo de Solicitud y Ruta

Resumen
- Este documento describe el flujo de negocio soportado por las APIs y las colecciones Postman incluidas en `postman/`.
- Las colecciones han sido actualizadas para usar el flujo canónico: generar opciones y confirmarlas mediante el recurso `solicitudes`.

Precondiciones
- Servicios levantados localmente (por ejemplo usando `docker-compose up` del repo) y escuchando en `{{baseUrl}}` (por defecto `http://localhost:8080`).
- Keycloak disponible y variables de colección configuradas (`keycloakUrl`, `client_id`, `client_secret`).
- Variable `access_token` guardada en la colección/environment antes de ejecutar los pasos (use el request de Auth en la colección).

Paso a paso (mapa a requests Postman)

1) Obtener token (cambiar rol si hace falta)
- Postman: carpeta `Auth / Switch Role` → `Get Token - cliente1 (CLIENTE)` o `Get Token - responsable1 (OPERADOR)`.
- Resultado: `access_token` en variables de colección.

2) Crear contenedor (si aplica)
- Request: `Lifecycle Flow` → `Create Contenedor`.
- Ruta: `POST /api/v1/contenedores`.
- Uso: generar un `contenedorId` para la solicitud.

3) Crear solicitud (cliente)
- Request: `Lifecycle Flow` → `Create Solicitud`.
- Ruta: `POST /api/v1/solicitudes`.
- Resultado: guarda `solicitudId` en variables de la colección.

4) (Operador) Solicitar ruta para la solicitud
- Request: `Lifecycle Flow` → `Solicitar Ruta (operador)`.
- Ruta: `POST /api/v1/solicitudes/{solicitudId}/solicitar-ruta`.
- Resultado: el servicio generará una `Ruta` tentativa y/o persistirá la ruta directa.

5) Generar y persistir Opciones (variantes)
- Request: `Lifecycle Flow` → `Create Opciones for Solicitud (persist)`.
- Ruta: `POST /api/v1/solicitudes/{solicitudId}/opciones` con body `{ "calcularVariantes": true }`.
- Notas: si desea sólo calcular (sin persistir), puede usar la query `?calcularVariantes=true`.

6) Listar Opciones disponibles
- Request: `Lifecycle Flow` → `List Opciones for Solicitud`.
- Ruta: `GET /api/v1/solicitudes/{solicitudId}/opciones`.
- Uso: revisar variantes (cada opcion incluye una `rutaTentativa` o referencia a la `ruta` creada al confirmar).

7) Confirmar / Seleccionar una Opción
- Request: `Lifecycle Flow` → `Confirm Opcion Persistida` (o `Select Opcion (confirmar via Solicitud)` en la colección general).
- Ruta: `POST /api/v1/solicitudes/{solicitudId}/opciones/{opcionId}/confirmar`.
- Comportamiento: la confirmación siempre se realiza utilizando el `opcionId` (la opción previamente persistida). El body no es necesario — si se envía será ignorado y la API confirmará la opción referenciada por `opcionId`.
- Resultado: se crea/retorna la `Ruta` final (generada por `ms-rutas`) y la `solicitud` se parchea para apuntar a `rutaId`.
- Postman: los scripts de test actualizan `rutaId` en variables y envían automáticamente el `PATCH /api/v1/solicitudes/{solicitudId}/ruta?rutaId={rutaId}` para mantener sincronía.

8) Asignaciones y Ejecución
- Asignar contenedor: `POST /api/v1/solicitudes/{solicitudId}/asignar-contenedor?contenedorId={contenedorId}`.
- Calcular precio estimado: `POST /api/v1/solicitudes/{solicitudId}/calcular-precio`.
- Asignar transporte: `POST /api/v1/solicitudes/{solicitudId}/asignar-transporte?transportistaId={transportistaId}`.
- Finalizar: `PATCH /api/v1/solicitudes/{solicitudId}/finalizar?costoFinal={}&tiempoReal={}`.

9) Tramos y seguimiento de la ejecución
- Listar tramos por ruta: `GET /api/v1/tramos/por-ruta/{rutaId}`.
- Iniciar / Finalizar tramo: `POST /api/v1/rutas/{rutaId}/tramos/{tramoId}/iniciar` y `POST /api/v1/rutas/{rutaId}/tramos/{tramoId}/finalizar?fechaHoraReal={}`.
- Asignar transportista a tramo: `POST /api/v1/tramos/{tramoId}/asignar-transportista?camionId={}&dominio={}`.

Buenas prácticas y notas
- Preferir siempre el flujo `solicitudes/{solicitudId}/opciones` para generar/confirmar opciones; los endpoints antiguos basados en `rutas` fueron removidos.
- El endpoint `POST /api/v1/solicitudes/{solicitudId}/opciones` acepta `{"calcularVariantes": true}` para que el servidor genere y persista variantes.
- Los scripts de las colecciones guardan `solicitudId`, `rutaId` y `opciones` en variables de colección para facilitar ejecuciones encadenadas.
- Para pruebas automatizadas locales use `newman` con la colección actualizada:

  ```powershell
  # instalar newman (si no está instalado)
  npm i -g newman
  # ejecutar la colección
  newman run postman/TPI-Backend-Lifecycle.postman_collection.json -e <env-file>.json
  ```

Cambios aplicados a las colecciones
- Todas las referencias a `/api/v1/rutas/{rutaId}/opciones` y `/api/v1/rutas/{rutaId}/opciones/{opcionId}/seleccionar` fueron reemplazadas por las rutas canónicas en `/api/v1/solicitudes/{solicitudId}/opciones` y `/api/v1/solicitudes/{solicitudId}/opciones/{opcionId}/confirmar`.

Siguientes pasos recomendados
- Ejecutar las colecciones en local (o en CI usando `newman`) para validar el flujo end-to-end.
- Parametrizar variables de colección para distintos entornos (dev/staging/prod).
- Añadir tests de integración que simulen el flujo completo (crear solicitud → generar opciones → confirmar → ejecutar tramos).

Si quieres, puedo:
- Ejecutar un `newman run` aquí (necesitarás proporcionar un archivo de entorno JSON o permitir que use las variables por defecto).
- Convertir la colección actualizada en un `README` más visual o en un documento con capturas de ejemplo.

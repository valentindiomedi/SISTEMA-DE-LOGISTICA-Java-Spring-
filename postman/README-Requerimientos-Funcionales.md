# Colección Postman - Requerimientos Funcionales TPI Backend

Esta colección de Postman implementa todos los **requerimientos funcionales mínimos** del sistema de transporte de contenedores.

## 📋 Contenido

La colección está organizada en **11 secciones principales** que cubren los requerimientos funcionales:

### 0. Autenticación
Endpoints para obtener tokens de acceso según el rol del usuario:
- **Get Token - Cliente**: Usuario con rol `CLIENTE`
- **Get Token - Operador**: Usuario con rol `OPERADOR`
- **Get Token - Administrador**: Usuario con rol `ADMIN`
- **Get Token - Transportista**: Usuario con rol `TRANSPORTISTA`

---

## ✅ Requerimientos Funcionales Implementados

### RF1: Registrar Nueva Solicitud de Transporte (CLIENTE)

**Carpeta:** `1. Registrar Solicitud (CLIENTE)`

Este requerimiento incluye:
- ✅ Creación de contenedor con identificación única
- ✅ Registro automático del cliente si no existe previamente
- ✅ Estados de solicitud: [BORRADOR - PROGRAMADA - EN_TRANSITO - ENTREGADA]

**Endpoints:**
1. **1.1 Registro de Cliente** - Endpoint público para auto-registro
2. **1.2 Crear Solicitud con Contenedor Nuevo** - Crea solicitud + contenedor automáticamente
3. **1.3 Crear Solicitud con Contenedor Existente** - Reutiliza un contenedor existente
4. **1.4 Ver Estados Disponibles** - Consulta estados permitidos

**Flujo de uso:**
```
1. [Opcional] Registrar cliente (si no existe)
2. Crear solicitud (con nuevo contenedor o existente)
3. Estado inicial: BORRADOR
```

**Ejemplo de Request (1.2):**
```json
POST {{baseUrl}}/api/v1/solicitudes
Authorization: Bearer {{access_token}}

{
  "direccionOrigen": "Av. Santa Fe 1234, CABA, Argentina",
  "direccionDestino": "Av. Corrientes 5678, CABA, Argentina",
  "clienteEmail": "juan.perez@example.com",
  "clienteNombre": "Juan Pérez",
  "clienteTelefono": "+54911123456",
  "contenedorPeso": 500.0,
  "contenedorVolumen": 5.0
}
```

---

### RF2: Consultar Estado del Transporte (CLIENTE)

**Carpeta:** `2. Consultar Estado Transporte (CLIENTE)`

Permite al cliente consultar el estado del transporte de un contenedor.

**Endpoints:**
1. **2.1 Consultar Seguimiento de Solicitud** - Estado completo de la solicitud
2. **2.2 Consultar Estado del Contenedor** - Estado y ubicación del contenedor
3. **2.3 Ver Detalles de Solicitud** - Información completa de la solicitud
4. **2.4 Listar Mis Solicitudes** - Todas las solicitudes del cliente

**Información incluida:**
- Estado actual (BORRADOR / PROGRAMADA / EN_TRANSITO / ENTREGADA)
- Ubicación del contenedor
- Ruta asignada con tramos
- Estimación de tiempo de entrega
- Historial de cambios de estado

**Ejemplo:**
```
GET {{baseUrl}}/api/v1/solicitudes/{{solicitudId}}/seguimiento
Authorization: Bearer {{access_token}}
```

---

### RF3: Consultar Rutas Tentativas (OPERADOR/ADMIN)

**Carpeta:** `3. Consultar Rutas Tentativas (OPERADOR/ADMIN)`

Consulta rutas tentativas con todos los tramos sugeridos, tiempo estimado y costo estimado.

**Endpoints:**
1. **3.1 Generar Opciones de Rutas** - Crea múltiples opciones de rutas
2. **3.2 Consultar Opciones Generadas** - Lista las opciones disponibles
3. **3.3 Ver Detalle de una Ruta** - Información completa de una ruta
4. **3.4 Listar Todas las Rutas** - Vista general del sistema

**Cada opción de ruta incluye:**
- ✅ Lista de tramos (origen → destino)
- ✅ Transportista y camión asignado a cada tramo
- ✅ Distancia total y por tramo (calculada con OSRM)
- ✅ Tiempo estimado total y por tramo
- ✅ Costo total y por tramo (según tarifas)
- ✅ Estado de cada tramo

**Ejemplo:**
```
POST {{baseUrl}}/api/v1/rutas/solicitudes/{{solicitudId}}/opciones
Authorization: Bearer {{access_token}}
```

**Response:**
```json
[
  {
    "id": 1,
    "distanciaTotal": 25.5,
    "tiempoEstimado": 45,
    "costoTotal": 1500.00,
    "tramos": [
      {
        "origen": "Av. Santa Fe 1234, CABA",
        "destino": "Deposito Central",
        "transportista": "Carlos Ramirez",
        "camion": "ABC123",
        "distancia": 12.5,
        "tiempo": 20,
        "costo": 750.00
      },
      // ... más tramos
    ]
  }
]
```

---

### RF4: Asignar Ruta a Solicitud (OPERADOR/ADMIN)

**Carpeta:** `4. Asignar Ruta a Solicitud (OPERADOR/ADMIN)`

Asigna una ruta completa con todos sus tramos a una solicitud.

**Endpoints:**
1. **4.1 Confirmar Opción de Ruta** - Asigna ruta desde solicitud
2. **4.2 Seleccionar Opción de Ruta** - Asigna ruta desde ruta
3. **4.3 Asignar Ruta Directa** - Asignación directa
4. **4.4 Ver Ruta Asignada** - Consulta ruta de una solicitud

**Proceso de asignación:**
```
1. Generar opciones de rutas (RF3)
2. Revisar opciones generadas
3. Confirmar opción seleccionada
4. Estado cambia: BORRADOR → PROGRAMADA
5. Se reservan recursos (transportistas, camiones)
6. Se calcula y asigna precio final
```

**Ejemplo:**
```
POST {{baseUrl}}/api/v1/solicitudes/{{solicitudId}}/opciones/{{opcionId}}/confirmar
Authorization: Bearer {{access_token}}
```

---

### RF5: Consultar Contenedores Pendientes (OPERADOR/ADMIN)

**Carpeta:** `5. Consultar Contenedores Pendientes (OPERADOR/ADMIN)`

Consulta todos los contenedores pendientes de entrega con su ubicación/estado y filtros.

**Endpoints:**
1. **5.1 Listar Todos los Contenedores** - Vista general
2. **5.2 Filtrar por Estado EN_TRANSITO** - Contenedores en tránsito
3. **5.3 Filtrar por Estado RESERVADO** - Contenedores asignados
4. **5.4 Consultar Solicitudes con Filtros** - Solicitudes filtradas
5. **5.5 Consultar Solicitudes EN_TRANSITO** - Solicitudes activas
6. **5.6 Ver Detalle de Contenedor** - Información completa
7. **5.7 Seguimiento Detallado** - Ubicación e historial

**Estados de Contenedor:**
- `LIBRE` - Disponible para asignar
- `RESERVADO` - Asignado a una solicitud
- `EN_TRANSITO` - En proceso de entrega ✅ **PENDIENTE**
- `ENTREGADO` - Ya entregado

**Filtros disponibles:**
```
GET {{baseUrl}}/api/v1/contenedores?estado=EN_TRANSITO
GET {{baseUrl}}/api/v1/contenedores?estado=RESERVADO
GET {{baseUrl}}/api/v1/solicitudes?estado=PROGRAMADA
GET {{baseUrl}}/api/v1/solicitudes?estado=EN_TRANSITO
```

---

### 6. Gestión de Estados (OPERADOR/ADMIN)

**Carpeta:** Complementaria - Para gestionar el ciclo de vida

**Endpoints:**
1. **6.1 Cambiar Estado de Solicitud** - Actualiza estado manualmente
2. **6.2 Iniciar Tramo de Ruta** - Comienza un tramo
3. **6.3 Finalizar Tramo de Ruta** - Completa un tramo
4. **6.4 Finalizar Solicitud Completa** - Marca como entregada

**Transiciones de estado:**
```
SOLICITUD:
BORRADOR → PROGRAMADA → EN_TRANSITO → ENTREGADA

CONTENEDOR:
LIBRE → RESERVADO → EN_TRANSITO → ENTREGADO
```

---

### RF6: Asignar Camión a Tramo (OPERADOR/ADMIN)

**Carpeta:** `7. Asignar Camión a Tramo (OPERADOR/ADMIN)`

Permite asignar camiones a tramos de traslado con **validación automática de capacidad**.

**Endpoints:**
1. **7.1 Listar Camiones Disponibles** - Todos los camiones del sistema
2. **7.2 Ver Detalle de Camión** - Información completa incluyendo capacidades
3. **7.3 Asignar Camión a Tramo (con validación)** - ⚠️ **VALIDACIÓN DE CAPACIDAD**
4. **7.4 Registrar Nuevo Camión** - Alta de camión con capacidades
5. **7.5 Asignar Transportista a Camión** - Asignar/reasignar transportista
6. **7.6 Actualizar Estado de Camión** - Disponibilidad y estado activo

**Validación de Capacidad:**

Cuando se asigna un camión a un tramo, el sistema valida automáticamente:
- ✅ **Peso del contenedor ≤ Capacidad máxima del camión**
- ✅ **Volumen del contenedor ≤ Capacidad máxima del camión**

Si la validación falla, retorna **HTTP 400** con mensaje descriptivo.

**Ejemplo de asignación:**
```
POST {{baseUrl}}/api/v1/tramos/{{tramoId}}/asignar-transportista?dominio=ABC123
```

**Response exitoso (200):**
```json
{
  "id": 1,
  "camionDominio": "ABC123",
  "nombreTransportista": "Carlos Ramirez",
  "estado": "PENDIENTE"
}
```

**Response con error de validación (400):**
```json
{
  "error": "Validación de capacidad fallida",
  "mensaje": "El peso del contenedor (15000 kg) supera la capacidad del camión (10000 kg)"
}
```

---

### RF7: Determinar Inicio/Fin de Tramo (TRANSPORTISTA)

**Carpeta:** `8. Gestión de Tramos (TRANSPORTISTA)`

Permite a los transportistas marcar el inicio y fin de cada tramo de traslado.

**Endpoints:**
1. **8.1 Listar Tramos de una Ruta** - Ver todos los tramos asignados
2. **8.2 Iniciar Tramo (TRANSPORTISTA)** - ▶️ Marca inicio del traslado
3. **8.3 Finalizar Tramo (TRANSPORTISTA)** - ⏹️ Marca fin del traslado
4. **8.4 Actualizar Fecha de Llegada** - Ajuste manual de fecha/hora

**Flujo de Transportista:**

```
1. Listar tramos de la ruta asignada
2. Al comenzar traslado → Iniciar Tramo
   - Sistema registra fecha/hora real de inicio
   - Estado del tramo → EN_PROCESO
   - Estado del contenedor → EN_TRANSITO
3. Al completar traslado → Finalizar Tramo
   - Sistema registra fecha/hora real de fin
   - Estado del tramo → COMPLETADO
   - Calcula tiempo real del tramo
   - Calcula estadía en depósito (si corresponde)
4. Si es el último tramo → Solicitud → ENTREGADA
```

**Ejemplo - Iniciar tramo:**
```
POST {{baseUrl}}/api/v1/rutas/{{rutaId}}/tramos/{{tramoId}}/iniciar
Authorization: Bearer {{access_token}}
```

**Ejemplo - Finalizar tramo:**
```
POST {{baseUrl}}/api/v1/rutas/{{rutaId}}/tramos/{{tramoId}}/finalizar
Authorization: Bearer {{access_token}}
```

---

### RF8: Calcular Costo Total de Entrega

**Carpeta:** `9. Cálculo de Costos (OPERADOR/ADMIN)`

Calcula el costo total de entrega incluyendo **todos los factores**:

**Factores de cálculo:**
- 📏 **Recorrido total**: Distancia entre origen → depósitos → destino (usando OSRM)
- ⚖️ **Peso del contenedor**: Según tarifas por rango de peso
- 📦 **Volumen del contenedor**: Según tarifas por rango de volumen
- 🏢 **Estadía en depósitos**: Calculada a partir de la **diferencia entre fechas reales** de entrada y salida

**Endpoints:**
1. **9.1 Calcular Precio Estimado** - Cálculo previo (sin estadía)
2. **9.2 Calcular Costo para Solicitud** - Costo basado en solicitud específica
3. **9.3 Calcular Distancias de Ruta** - Distancias reales con OSRM
4. **9.4 Calcular Costos de Ruta** - Costos por distancia, peso y volumen
5. **9.5 Calcular Todo (Distancias + Costos)** - Cálculo completo en un solo paso
6. **9.6 Registrar Cálculo Final en Solicitud** - ⭐ **Cálculo real con estadía**

**Cálculo de Estadía en Depósitos:**

La estadía se calcula automáticamente cuando se finalizan los tramos:

```
Estadía en Depósito X = Fecha Salida Real - Fecha Llegada Real

Donde:
- Fecha Llegada Real = Fin del tramo que llega al depósito
- Fecha Salida Real = Inicio del tramo que sale del depósito

Costo Estadía = (Horas de estadía) × (Tarifa del depósito)
```

**Ejemplo:**
```
Tramo 1: Origen → Depósito A
  - Finaliza: 2025-11-23 10:00:00 ← Llegada al depósito

Tramo 2: Depósito A → Destino
  - Inicia: 2025-11-23 14:00:00 ← Salida del depósito

Estadía = 14:00 - 10:00 = 4 horas
Costo Estadía = 4 horas × $100/hora = $400
```

**Endpoint de cálculo final:**
```
POST {{baseUrl}}/api/v1/solicitudes/{{solicitudId}}/calcular-precio
```

Este endpoint se ejecuta **al finalizar la entrega** y registra:
- ✅ Distancia real recorrida
- ✅ Tiempo real total
- ✅ Costo por distancia
- ✅ Costo por peso y volumen
- ✅ **Costo por estadía en cada depósito**
- ✅ Costo total final

---

### RF9: Gestión de Depósitos, Camiones y Tarifas

**Carpetas:**
- `10. Gestión de Depósitos (OPERADOR/ADMIN)`
- `7. Asignar Camión a Tramo` (incluye gestión de camiones)
- `11. Gestión de Tarifas (OPERADOR/ADMIN)`

#### Gestión de Depósitos

**Endpoints:**
1. **10.1 Listar Depósitos** - Todos los depósitos del sistema
2. **10.2 Ver Detalle de Depósito** - Información completa
3. **10.3 Crear Depósito** - Registrar nuevo depósito
4. **10.4 Actualizar Depósito** - Modificar información
5. **10.5 Obtener Coordenadas** - GPS para cálculos de ruta

**Datos de un depósito:**
```json
{
  "nombre": "Depósito Central",
  "direccion": "Av. Corrientes 1500, CABA",
  "latitud": -34.603722,
  "longitud": -58.381592,
  "capacidad": 1000,
  "tarifaEstadia": 100.0,
  "activo": true
}
```

#### Gestión de Camiones

**Endpoints:** (en carpeta 7)
1. **7.1 Listar Camiones** - Todos los camiones
2. **7.2 Ver Detalle** - Info completa con capacidades
3. **7.4 Registrar Camión** - Alta de nuevo camión
4. **7.5 Asignar Transportista** - Asignar/reasignar
5. **7.6 Actualizar Estado** - Disponibilidad

**Datos de un camión:**
```json
{
  "dominio": "ABC123",
  "capacidadPeso": 15000.0,
  "capacidadVolumen": 40.0,
  "nombreTransportista": "Carlos Ramirez",
  "costoKm": 50.0,
  "disponible": true,
  "activo": true
}
```

#### Gestión de Tarifas

**Endpoints:**
1. **11.1 Listar Tarifas** - Todas las tarifas
2. **11.2 Ver Detalle** - Tarifa con rangos
3. **11.3 Crear Tarifa** - Nueva tarifa base
4. **11.4 Actualizar Tarifa** - Modificar tarifa
5. **11.5 Agregar Rango** - Rango de peso/volumen
6. **11.6 Actualizar Rango** - Modificar rango

**Estructura de tarifa:**
```json
{
  "nombre": "Tarifa Estándar",
  "descripcion": "Tarifa para contenedores estándar",
  "costoBase": 500.0,
  "costoPorKm": 10.0,
  "activa": true,
  "rangos": [
    {
      "pesoMin": 0.0,
      "pesoMax": 1000.0,
      "volumenMin": 0.0,
      "volumenMax": 10.0,
      "multiplicador": 1.0
    }
  ]
}
```

---

## 🚀 Cómo Usar la Colección

### 1. Importar en Postman

1. Abrir Postman
2. Click en "Import"
3. Seleccionar el archivo: `TPI-Backend-Requerimientos-Funcionales.postman_collection.json`
4. La colección aparecerá en tu workspace

### 2. Configurar Variables

La colección usa las siguientes variables:

| Variable | Valor por Defecto | Descripción |
|----------|------------------|-------------|
| `baseUrl` | `http://localhost:8080` | URL del API Gateway |
| `keycloakUrl` | `http://localhost:8089` | URL de Keycloak |
| `client_id` | `postman-test` | Client ID de Keycloak |
| `client_secret` | `secret-postman-123` | Client Secret |
| `access_token` | (automático) | Token JWT (se obtiene automáticamente) |
| `solicitudId` | (automático) | ID de solicitud creada |
| `contenedorId` | (automático) | ID de contenedor creado |
| `clienteId` | (automático) | ID de cliente creado |
| `rutaId` | (automático) | ID de ruta creada |
| `opcionId` | (automático) | ID de opción seleccionada |
| `tramoId` | (automático) | ID de tramo |
| `camionDominio` | (automático) | Dominio/patente del camión |
| `depositoId` | (automático) | ID de depósito |
| `tarifaId` | (automático) | ID de tarifa |

**Nota:** Las variables con "(automático)" se configuran automáticamente mediante scripts de test.

### 3. Flujo de Prueba Completo

#### Flujo como CLIENTE:

```
1. [0. Autenticación] Get Token - Cliente
2. [1.1] Registro de Cliente (si no existe)
3. [1.2] Crear Solicitud con Contenedor Nuevo
4. [2.1] Consultar Seguimiento de Solicitud
5. [2.2] Consultar Estado del Contenedor
```

#### Flujo como OPERADOR/ADMIN:

```
1. [0. Autenticación] Get Token - Operador
2. [3.1] Generar Opciones de Rutas para Solicitud
3. [3.2] Consultar Opciones de Rutas Generadas
4. [4.1] Confirmar Opción de Ruta
5. [7.1] Listar Camiones Disponibles
6. [7.3] Asignar Camión a Tramo (con validación de capacidad)
7. [5.2] Filtrar Contenedores por Estado EN_TRANSITO
8. [9.5] Calcular Todo (Distancias + Costos)
```

#### Flujo como TRANSPORTISTA:

```
1. [0. Autenticación] Get Token - Transportista
2. [8.1] Listar Tramos de una Ruta (asignados a mí)
3. [8.2] Iniciar Tramo de Ruta (al comenzar traslado)
4. [8.3] Finalizar Tramo de Ruta (al completar traslado)
5. [Repetir 3-4 para cada tramo]
```

#### Flujo Completo E2E (End-to-End):

```
=== CLIENTE ===
1. [0.1] Get Token - Cliente
2. [1.1] Registro de Cliente
3. [1.2] Crear Solicitud con Contenedor Nuevo
4. [2.1] Consultar Seguimiento

=== OPERADOR ===
5. [0.2] Get Token - Operador
6. [3.1] Generar Opciones de Rutas
7. [3.2] Consultar Opciones Generadas
8. [4.1] Confirmar Opción de Ruta
9. [7.1] Listar Camiones Disponibles
10. [7.3] Asignar Camión a cada Tramo
11. [9.5] Calcular Todo (Distancias + Costos)

=== TRANSPORTISTA ===
12. [0.4] Get Token - Transportista
13. [8.1] Listar Tramos de Ruta
14. [8.2] Iniciar Primer Tramo
15. [8.3] Finalizar Primer Tramo
16. [Repetir 14-15 para tramos restantes]

=== OPERADOR (Cierre) ===
17. [0.2] Get Token - Operador
18. [9.6] Registrar Cálculo Final (con estadía)
19. [6.4] Finalizar Solicitud Completa

=== CLIENTE (Verificación) ===
20. [0.1] Get Token - Cliente
21. [2.1] Consultar Seguimiento Final
```

### 4. Usuarios Pre-configurados

El sistema viene con usuarios pre-cargados en Keycloak:

| Username | Password | Rol | Uso |
|----------|----------|-----|-----|
| `cliente1` | `1234` | CLIENTE | Crear y consultar solicitudes |
| `responsable1` | `1234` | OPERADOR | Gestionar rutas y asignaciones |
| `tester` | `1234` | ADMIN | Administración completa |
| `carlos.ramirez` | `1234` | TRANSPORTISTA | Iniciar/finalizar tramos |

---

## 📊 Mapeo de Requerimientos vs Endpoints

| Requerimiento | Endpoints Principales | Carpeta |
|---------------|----------------------|---------|
| **RF1** - Registrar solicitud con contenedor y cliente | `POST /api/v1/solicitudes`<br>`POST /api/v1/clientes/registro` | 1 |
| **RF2** - Consultar estado del transporte | `GET /api/v1/solicitudes/{id}/seguimiento`<br>`GET /api/v1/contenedores/{id}/seguimiento` | 2 |
| **RF3** - Consultar rutas tentativas | `POST /api/v1/rutas/solicitudes/{id}/opciones`<br>`GET /api/v1/rutas/solicitudes/{id}/opciones` | 3 |
| **RF4** - Asignar ruta a solicitud | `POST /api/v1/solicitudes/{id}/opciones/{opcionId}/confirmar` | 4 |
| **RF5** - Consultar contenedores pendientes con filtros | `GET /api/v1/contenedores?estado=EN_TRANSITO`<br>`GET /api/v1/contenedores/{id}/seguimiento` | 5 |
| **RF6** - Asignar camión a tramo (validar capacidad) | `POST /api/v1/tramos/{id}/asignar-transportista` | 7 |
| **RF7** - Inicio/fin de tramo (Transportista) | `POST /api/v1/rutas/{id}/tramos/{tramoId}/iniciar`<br>`POST /api/v1/rutas/{id}/tramos/{tramoId}/finalizar` | 8 |
| **RF8** - Calcular costo total (distancia, peso, volumen, estadía) | `POST /api/v1/solicitudes/{id}/calcular-precio`<br>`POST /api/v1/rutas/{id}/calcular-completo` | 9 |
| **RF9a** - Gestión de depósitos | `GET/POST/PATCH /api/v1/depositos` | 10 |
| **RF9b** - Gestión de camiones | `GET/POST/PATCH /api/v1/camiones` | 7 |
| **RF9c** - Gestión de tarifas | `GET/POST/PATCH /api/v1/tarifas` | 11 |
| **RF10** - Validar capacidad de camión | `POST /api/v1/tramos/{id}/asignar-transportista` (validación automática) | 7 |

---

## 🔒 Seguridad y Roles

### Permisos por Endpoint

| Endpoint | CLIENTE | OPERADOR | ADMIN | TRANSPORTISTA |
|----------|---------|----------|-------|---------------|
| Registro de cliente (público) | ✅ | ✅ | ✅ | ✅ |
| Crear solicitud | ✅ | ✅ | ✅ | ❌ |
| Consultar mis solicitudes | ✅ (solo propias) | ✅ (todas) | ✅ (todas) | ❌ |
| Generar rutas tentativas | ❌ | ✅ | ✅ | ❌ |
| Asignar ruta | ❌ | ✅ | ✅ | ❌ |
| Consultar contenedores con filtros | ❌ | ✅ | ✅ | ❌ |
| Asignar camión a tramo | ❌ | ✅ | ✅ | ❌ |
| Iniciar/finalizar tramo | ❌ | ✅ | ✅ | ✅ |
| Calcular costos | ❌ | ✅ | ✅ | ❌ |
| Gestionar depósitos | ❌ | ✅ | ✅ | ❌ |
| Gestionar camiones | ❌ | ✅ | ✅ | ❌ |
| Gestionar tarifas | ❌ | ✅ | ✅ | ❌ |
| Gestionar estados | ❌ | ✅ | ✅ | ✅ (tramos) |

---

## 📝 Notas Importantes

### Estados del Sistema

**Solicitudes:**
- `BORRADOR` - Recién creada, sin ruta asignada
- `PROGRAMADA` - Con ruta confirmada, lista para iniciar
- `EN_TRANSITO` - En proceso de transporte
- `ENTREGADA` - Completada exitosamente

**Contenedores:**
- `LIBRE` - Disponible
- `RESERVADO` - Asignado pero no en tránsito
- `EN_TRANSITO` - Siendo transportado
- `ENTREGADO` - Entrega completada

### Validaciones Automáticas

1. **Registro de Cliente**: El sistema valida que email y username sean únicos
2. **Creación de Solicitud**: Si el cliente no existe, se crea automáticamente usando el email
3. **Asignación de Ruta**: Valida que la solicitud esté en estado `BORRADOR`
4. **Transiciones de Estado**: Solo permite transiciones válidas según la máquina de estados

### Scripts de Test Automáticos

Los requests incluyen scripts que:
- Guardan automáticamente IDs en variables de colección
- Validan responses
- Muestran mensajes de éxito/error en la consola
- Facilitan el flujo secuencial de pruebas

---

## 🆘 Troubleshooting

### Error: "Token inválido o expirado"
**Solución:** Ejecutar nuevamente el endpoint de autenticación correspondiente (0.1, 0.2 o 0.3)

### Error: "Cliente no encontrado"
**Solución:** Ejecutar primero el endpoint 1.1 para registrar el cliente

### Error: "No se puede cambiar al estado X"
**Solución:** Verificar el flujo de estados permitidos (consultar endpoint 1.4)

### Error: "Contenedor no disponible"
**Solución:** Verificar que el contenedor esté en estado `LIBRE` o usar la opción de crear uno nuevo

---

## 📚 Recursos Adicionales

- **Documentación del Backend**: Ver `README.md` en la raíz del proyecto
- **Guía de Proceso de Negocio**: Ver `docs/BusinessProcessGuide.md`
- **Colección General**: `TPI-Backend-General.postman_collection.json` (incluye más endpoints)
- **Colección de Lifecycle**: `TPI-Backend-Lifecycle.postman_collection.json` (flujo completo E2E)

---

## ✅ Checklist de Validación

Use este checklist para validar que todos los requerimientos funcionales estén operativos:

**RF1 - Registro de Solicitudes:**
- [ ] Cliente puede registrarse y crear solicitud con contenedor nuevo
- [ ] Sistema crea contenedor con ID único automáticamente
- [ ] Sistema registra cliente si no existe (por email)
- [ ] Solicitud se crea en estado BORRADOR

**RF2 - Consulta de Estado:**
- [ ] Cliente puede consultar estado de su solicitud
- [ ] Cliente puede ver ubicación de su contenedor
- [ ] Sistema muestra historial de cambios de estado

**RF3 - Rutas Tentativas:**
- [ ] Operador puede generar múltiples opciones de rutas
- [ ] Cada opción muestra tramos, tiempos y costos estimados
- [ ] Sistema calcula distancias con OSRM

**RF4 - Asignación de Ruta:**
- [ ] Operador puede asignar una ruta a una solicitud
- [ ] Al asignar ruta, solicitud pasa a estado PROGRAMADA
- [ ] Sistema reserva recursos (transportistas, camiones)

**RF5 - Contenedores Pendientes:**
- [ ] Operador puede filtrar contenedores EN_TRANSITO
- [ ] Operador puede filtrar contenedores RESERVADOS
- [ ] Operador puede ver ubicación de cada contenedor pendiente
- [ ] Sistema permite filtrar solicitudes por estado

**RF6 - Asignación de Camión:**
- [ ] Operador puede asignar camión a un tramo
- [ ] Sistema valida que peso del contenedor ≤ capacidad del camión
- [ ] Sistema valida que volumen del contenedor ≤ capacidad del camión
- [ ] Sistema rechaza asignación si supera capacidad (HTTP 400)
- [ ] Operador puede registrar nuevos camiones con capacidades

**RF7 - Inicio/Fin de Tramo:**
- [ ] Transportista puede ver sus tramos asignados
- [ ] Transportista puede marcar inicio de tramo
- [ ] Sistema registra fecha/hora real de inicio
- [ ] Transportista puede marcar fin de tramo
- [ ] Sistema registra fecha/hora real de finalización
- [ ] Sistema calcula tiempo real del tramo

**RF8 - Cálculo de Costos:**
- [ ] Sistema calcula distancia total (origen → depósitos → destino)
- [ ] Sistema calcula costo por distancia
- [ ] Sistema calcula costo por peso del contenedor
- [ ] Sistema calcula costo por volumen del contenedor
- [ ] Sistema calcula estadía en depósitos (diferencia de fechas reales)
- [ ] Sistema registra cálculo de tiempo real en solicitud
- [ ] Sistema registra cálculo de costo real en solicitud
- [ ] Al finalizar: costo total incluye todos los factores

**RF9 - Gestión de Recursos:**
- [ ] Operador puede crear/actualizar depósitos
- [ ] Operador puede crear/actualizar camiones
- [ ] Operador puede crear/actualizar tarifas
- [ ] Operador puede definir rangos de peso/volumen en tarifas
- [ ] Sistema permite consultar coordenadas de depósitos

**RF10 - Validación de Capacidad:**
- [ ] Sistema valida capacidad al asignar camión
- [ ] Sistema muestra mensaje claro si excede capacidad de peso
- [ ] Sistema muestra mensaje claro si excede capacidad de volumen
- [ ] Validación ocurre antes de confirmar asignación

---

## 🎯 Resumen de Nuevos Requerimientos

### ✨ Funcionalidades Principales Añadidas

**1. Validación de Capacidad de Camiones** 🚛
- Validación automática al asignar camión a tramo
- Verifica peso y volumen vs capacidades máximas
- Retorna error descriptivo si excede límites

**2. Gestión de Tramos por Transportista** 📍
- Transportistas pueden iniciar/finalizar tramos
- Registro de fechas/horas reales
- Cálculo automático de tiempos reales

**3. Cálculo Completo de Costos** 💰
- Distancia (OSRM): origen → depósitos → destino
- Peso y volumen según tarifas por rango
- **Estadía en depósitos**: basada en diferencia de fechas reales
- Registro de tiempo real y costo real en solicitud

**4. Gestión de Recursos del Sistema** ⚙️
- **Depósitos**: Alta, modificación, consulta de coordenadas
- **Camiones**: Alta con capacidades, asignación de transportistas
- **Tarifas**: Configuración de costos base, por km, y rangos

### 📊 Estadísticas de la Colección

- **Total de Endpoints**: 58
- **Carpetas**: 11
- **Roles Soportados**: 4 (Cliente, Operador, Admin, Transportista)
- **Requerimientos Funcionales**: 10 (RF1-RF10)
- **Variables Automáticas**: 9
- **Scripts de Test**: Incluidos en endpoints clave

### 🔑 Puntos Clave de Implementación

1. **Validación de Capacidad**: Automática y obligatoria al asignar camiones
2. **Cálculo de Estadía**: Basado en diferencia entre fechas reales (no estimadas)
3. **Roles y Permisos**: Estrictamente controlados por endpoint
4. **Trazabilidad Completa**: Cada acción registra fecha/hora y usuario
5. **Flujo End-to-End**: Desde solicitud hasta entrega con cálculo final

---

**Versión:** 2.0  
**Fecha:** Noviembre 2025  
**Autor:** TPI Backend Team  
**Última Actualización:** Ampliación con RF6-RF10

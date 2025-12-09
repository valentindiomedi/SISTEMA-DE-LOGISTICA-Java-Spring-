# TPI Backend - Sistema de Logística

Un sistema de logística backend moderno construido con **Spring Boot 3.3.4**, **Java 21** y arquitectura de microservicios. Gestiona solicitudes de transporte, cálculo de rutas, tarifas y seguimiento de entregas.

## 📋 Tabla de Contenidos

- [Características](#características)
- [Tecnologías](#tecnologías)
- [Arquitectura](#arquitectura)
- [Requisitos Previos](#requisitos-previos)
- [Instalación](#instalación)
- [Configuración](#configuración)
- [Uso](#uso)
- [API Endpoints](#api-endpoints)
- [Estructura del Proyecto](#estructura-del-proyecto)
- [Variables de Entorno](#variables-de-entorno)
- [Contribución](#contribución)
- [Licencia](#licencia)

## ✨ Características

- **API Gateway**: Enrutamiento centralizado con seguridad OAuth2
- **Gestión de Solicitudes**: Crear, seguir y gestionar solicitudes de transporte
- **Cálculo de Rutas**: Integración con OSRM para optimización de rutas
- **Tarificación**: Sistema dinámico de cálculo de precios por volumen y peso
- **Autenticación**: Integración con Keycloak para OAuth2 y OIDC
- **Base de Datos**: PostgreSQL con JPA/Hibernate
- **Containerización**: Docker y Docker Compose para fácil despliegue
- **API REST**: Documentación con Swagger/OpenAPI

## 🛠️ Tecnologías

- **Runtime**: Java 21 (OpenJDK)
- **Framework**: Spring Boot 3.3.4
- **Cloud**: Spring Cloud 2023.0.2
- **Base de Datos**: PostgreSQL 15+
- **Autenticación**: Keycloak
- **Build**: Maven
- **Contenerización**: Docker & Docker Compose
- **Rutas**: OSRM (Open Source Routing Machine)
- **Librerías**: Lombok, JPA/Hibernate, Spring Security, OAuth2

## 🏗️ Arquitectura

El sistema está organizado en una arquitectura de microservicios con un API Gateway centralizado:

```
┌─────────────────────────────────────────────────────────────┐
│                       API Gateway (8080)                     │
│            (Spring Cloud Gateway + OAuth2 + JWT)             │
└────────┬──────────────┬──────────────┬──────────────┘
         │              │              │
    ┌────▼────┐    ┌────▼────┐    ┌────▼────┐
    │   MS    │    │   MS    │    │   MS    │
    │Solicitu-│    │ Gestión │    │ Rutas   │
    │des      │    │Cálculos │    │Transpor-│
    │ (8083)  │    │ (8081)  │    │tistas   │
    │         │    │         │    │ (8082)  │
    └────┬────┘    └────┬────┘    └────┬────┘
         │              │              │
         └──────────────┼──────────────┘
                        │
                   ┌────▼──────┐
                   │PostgreSQL  │
                   │ (5432)     │
                   └────────────┘
```

### Microservicios

| Servicio | Puerto | Descripción |
|----------|--------|-------------|
| **api-gateway** | 8080 | Enrutador centralizado, autenticación OAuth2 |
| **ms-solicitudes** | 8083 | Gestión de solicitudes y contenedores |
| **ms-gestion-calculos** | 8081 | Cálculo de tarifas, precios y distancias |
| **ms-rutas-transportistas** | 8082 | Optimización de rutas y gestión de transportistas |

### Servicios Externos

- **PostgreSQL** (5432): Base de datos relacional
- **Keycloak** (8089): Servidor de identidad y autenticación
- **OSRM** (5000): Motor de enrutamiento

## 📋 Requisitos Previos

- **Docker** y **Docker Compose** (v2.0+)
- O alternativamente:
  - **Java 21** (OpenJDK)
  - **Maven 3.8+**
  - **PostgreSQL 15+**
  - **Keycloak 23+**

## 🚀 Instalación

### Opción 1: Con Docker Compose (Recomendado)

```bash
# Clonar el repositorio
git clone https://github.com/valentindiomedi/SISTEMA-DE-LOGISTICA-Java-Spring-.git
cd TPI-Backend

# Levantar todos los servicios
docker-compose up -d

# Ver logs en tiempo real
docker-compose logs -f
```

El sistema estará disponible en:
- **API Gateway**: http://localhost:8080
- **Keycloak**: http://localhost:8089
- **Swagger UI**: http://localhost:8080/swagger-ui.html

### Opción 2: Instalación Local

```bash
# Clonar el repositorio
git clone https://github.com/valentindiomedi/SISTEMA-DE-LOGISTICA-Java-Spring-.git
cd TPI-Backend

# Compilar todos los módulos
mvn clean package

# Ejecutar cada microservicio (en terminales separadas)
# Terminal 1: API Gateway
mvn spring-boot:run -pl api-gateway

# Terminal 2: MS Solicitudes
mvn spring-boot:run -pl ms-solicitudes

# Terminal 3: MS Gestión Cálculos
mvn spring-boot:run -pl ms-gestion-calculos

# Terminal 4: MS Rutas Transportistas
mvn spring-boot:run -pl ms-rutas-transportistas
```

## ⚙️ Configuración

### Variables de Entorno

Crea un archivo `.env` en la raíz del proyecto:

```env
# PostgreSQL
POSTGRES_DB=tpi_backend_db
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres

# Keycloak
KEYCLOAK_ADMIN=admin
KEYCLOAK_ADMIN_PASSWORD=admin

# Base URLs para comunicación inter-servicios
APP_TRANSPORTES_BASE_URL=http://ms-rutas-transportistas:8082
APP_CALCULOS_BASE_URL=http://ms-gestion-calculos:8081
APP_SOLICITUDES_BASE_URL=http://ms-solicitudes:8083

# Oauth2
SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=http://localhost:8089/realms/tpi-backend
```

### Configuración por Módulo

Cada módulo tiene su archivo `application.yml`:

- `api-gateway/src/main/resources/application.yml`
- `ms-solicitudes/src/main/resources/application.yml`
- `ms-gestion-calculos/src/main/resources/application.yml`
- `ms-rutas-transportistas/src/main/resources/application.yml`

## 📖 Uso

### 1. Autenticación

Obtener un token JWT:

```bash
curl -X POST http://localhost:8089/realms/tpi-backend/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=tpi-client&client_secret=your_secret&grant_type=password&username=user&password=pass"
```

### 2. Crear una Solicitud

```bash
curl -X POST http://localhost:8080/api/v1/solicitudes \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "origen": {"latitud": -34.6037, "longitud": -58.3816},
    "destino": {"latitud": -34.8581, "longitud": -58.6789},
    "contenedorId": 1
  }'
```

### 3. Generar Opciones de Ruta

```bash
curl -X POST http://localhost:8080/api/v1/solicitudes/{solicitudId}/opciones \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"calcularVariantes": true}'
```

### 4. Confirmar una Opción

```bash
curl -X POST http://localhost:8080/api/v1/solicitudes/{solicitudId}/opciones/{opcionId}/confirmar \
  -H "Authorization: Bearer YOUR_TOKEN"
```

## 🔌 API Endpoints

### Solicitudes

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| POST | `/api/v1/solicitudes` | Crear solicitud |
| GET | `/api/v1/solicitudes` | Listar solicitudes |
| GET | `/api/v1/solicitudes/{id}` | Obtener solicitud |
| PATCH | `/api/v1/solicitudes/{id}` | Actualizar solicitud |
| POST | `/api/v1/solicitudes/{id}/solicitar-ruta` | Solicitar ruta |
| POST | `/api/v1/solicitudes/{id}/opciones` | Generar opciones de ruta |
| GET | `/api/v1/solicitudes/{id}/opciones` | Listar opciones |
| POST | `/api/v1/solicitudes/{id}/opciones/{opcionId}/confirmar` | Confirmar opción |
| POST | `/api/v1/solicitudes/{id}/calcular-precio` | Calcular precio |
| POST | `/api/v1/solicitudes/{id}/asignar-transporte` | Asignar transportista |
| PATCH | `/api/v1/solicitudes/{id}/finalizar` | Finalizar solicitud |

### Rutas

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| POST | `/api/v1/rutas` | Crear ruta |
| GET | `/api/v1/rutas` | Listar rutas |
| GET | `/api/v1/rutas/{id}` | Obtener ruta |
| GET | `/api/v1/tramos/por-ruta/{rutaId}` | Listar tramos de ruta |
| POST | `/api/v1/rutas/{rutaId}/tramos/{tramoId}/iniciar` | Iniciar tramo |
| POST | `/api/v1/rutas/{rutaId}/tramos/{tramoId}/finalizar` | Finalizar tramo |

### Cálculos y Tarifas

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| GET | `/api/v1/tarifas` | Listar tarifas |
| POST | `/api/v1/tarifas` | Crear tarifa |
| POST | `/api/v1/distancia` | Calcular distancia |
| POST | `/api/v1/costo` | Calcular costo |

### Transportistas y Vehículos

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| GET | `/api/v1/camiones` | Listar vehículos |
| POST | `/api/v1/camiones` | Crear vehículo |

## 📁 Estructura del Proyecto

```
TPI-Backend/
├── api-gateway/              # API Gateway - Enrutador centralizado
│   ├── src/main/java/
│   │   └── com/backend/tpi/api_gateway/
│   │       ├── ApiGatewayApplication.java
│   │       ├── RouteConfig.java           # Configuración de rutas
│   │       ├── config/
│   │       │   ├── SecurityConfig.java
│   │       │   └── RestClientConfig.java
│   │       └── filters/
│   │           └── AuthorizationForwardFilter.java
│   ├── src/main/resources/
│   │   └── application.yml
│   ├── Dockerfile
│   └── pom.xml
│
├── ms-solicitudes/           # Gestión de Solicitudes de Transporte
│   ├── src/main/java/com/backend/tpi/ms_solicitudes/
│   │   ├── MsSolicitudesApplication.java
│   │   ├── controllers/          # REST Controllers
│   │   ├── services/             # Lógica de negocio
│   │   ├── repositories/         # Acceso a datos
│   │   ├── models/               # Entidades JPA
│   │   ├── dtos/                 # Data Transfer Objects
│   │   ├── exceptions/           # Manejadores de excepciones
│   │   └── config/               # Configuración
│   ├── src/main/resources/
│   │   └── application.yml
│   ├── Dockerfile
│   └── pom.xml
│
├── ms-gestion-calculos/      # Gestión de Tarifas y Cálculos
│   ├── src/main/java/com/backend/tpi/ms_gestion_calculos/
│   │   ├── GestionCalculosApplication.java
│   │   ├── controllers/
│   │   ├── services/
│   │   ├── repositories/
│   │   ├── models/
│   │   ├── dtos/
│   │   ├── exceptions/
│   │   └── config/
│   ├── src/main/resources/
│   │   └── application.yml
│   ├── Dockerfile
│   └── pom.xml
│
├── ms-rutas-transportistas/  # Gestión de Rutas y Transportistas
│   ├── src/main/java/com/backend/tpi/ms_rutas_transportistas/
│   │   ├── RutasTransportistasApplication.java
│   │   ├── controllers/
│   │   ├── services/
│   │   ├── repositories/
│   │   ├── models/
│   │   ├── dtos/
│   │   ├── clients/              # Clientes REST para otros servicios
│   │   ├── exceptions/
│   │   └── config/
│   ├── src/main/resources/
│   │   └── application.yml
│   ├── Dockerfile
│   └── pom.xml
│
├── postgres/                 # Scripts de base de datos
│   ├── 00-create-schema.sql
│   ├── 01-migrate-to-der.sql
│   └── init/
│       ├── 00-create-schema.sql
│       └── 04-insert-depositos.sql
│
├── keycloak/                 # Configuración de Keycloak
│   └── realm-tpi-backend.json
│
├── postman/                  # Colecciones Postman
│   ├── TPI-Backend-General.postman_collection.json
│   ├── TPI-Backend-Requerimientos-Funcionales.postman_collection.json
│   ├── env.local.postman_environment.json
│   └── README-Requerimientos-Funcionales.md
│
├── docs/                     # Documentación
│   └── BusinessProcessGuide.md
│
├── docker-compose.yml        # Orquestación de contenedores
├── docker-compose.osrm.yml   # Composición alternativa con OSRM
├── pom.xml                   # POM padre del proyecto
└── README.md                 # Este archivo
```

## 🗄️ Base de Datos

### Inicialización

Los scripts SQL se ejecutan automáticamente en Docker Compose:

1. **00-create-schema.sql**: Crea las tablas principales
2. **01-migrate-to-der.sql**: Migraciones adicionales
3. **04-insert-depositos.sql**: Datos iniciales

### Entidades Principales

- **Solicitud**: Solicitudes de transporte
- **Cliente**: Información del cliente
- **Contenedor**: Contenedores de carga
- **Ruta**: Rutas calculadas
- **Tramo**: Segmentos de una ruta
- **Tarifa**: Tarifas por zona/peso/volumen
- **Camion**: Vehículos disponibles
- **Transportista**: Conductores/transportistas

## 🔐 Seguridad

- **OAuth2/OIDC**: Integración con Keycloak
- **JWT**: Validación de tokens en API Gateway
- **CORS**: Configuración de seguridad en origen
- **Spring Security**: Protección de endpoints

### Roles Disponibles

- **CLIENTE**: Crear y seguir solicitudes
- **OPERADOR**: Gestionar rutas y asignaciones
- **TRANSPORTISTA**: Ejecutar entregas

## 🧪 Testing

### Ejecutar Tests Locales

```bash
# Tests de todos los módulos
mvn clean test

# Tests de un módulo específico
mvn clean test -pl ms-solicitudes

# Tests con cobertura
mvn clean test jacoco:report
```

### Pruebas con Postman

Importar las colecciones en Postman:
- `postman/TPI-Backend-General.postman_collection.json`
- `postman/TPI-Backend-Requerimientos-Funcionales.postman_collection.json`

O usar Newman:

```bash
npm install -g newman
newman run postman/TPI-Backend-General.postman_collection.json -e postman/env.local.postman_environment.json
```

## 📊 Monitoreo

Los logs se guardan en el directorio `logs/`:
- `logs/ms-solicitudes/`
- `logs/ms-gestion-calculos/`
- `logs/ms-rutas-transportistas/`

Ver logs en tiempo real:

```bash
docker-compose logs -f [service-name]
```

## 🐛 Troubleshooting

### Error de conexión a PostgreSQL

```bash
# Verificar que PostgreSQL está corriendo
docker-compose ps postgres

# Reiniciar PostgreSQL
docker-compose restart postgres
```

### Error de autenticación Keycloak

```bash
# Verificar Keycloak
curl http://localhost:8089/auth/admin/realms/tpi-backend

# Reiniciar Keycloak
docker-compose restart keycloak
```

### Puerto en uso

```bash
# Cambiar puerto en docker-compose.yml o application.yml
# Ejemplo: cambiar 8080 a 9090
```

## 🤝 Contribución

Las contribuciones son bienvenidas. Por favor:

1. Fork el repositorio
2. Crear una rama para tu feature (`git checkout -b feature/AmazingFeature`)
3. Commit tus cambios (`git commit -m 'Add some AmazingFeature'`)
4. Push a la rama (`git push origin feature/AmazingFeature`)
5. Abrir un Pull Request

## 📄 Licencia

Este proyecto está bajo la licencia MIT. Ver archivo `LICENSE` para más detalles.

## 👨‍💻 Autor

**Valentín Diomedi**
- GitHub: [@valentindiomedi](https://github.com/valentindiomedi)
- Repositorio: [SISTEMA-DE-LOGISTICA-Java-Spring-](https://github.com/valentindiomedi/SISTEMA-DE-LOGISTICA-Java-Spring-)

## 📞 Soporte

Para reportar bugs o solicitar features, abre un [GitHub Issue](https://github.com/valentindiomedi/SISTEMA-DE-LOGISTICA-Java-Spring-/issues).

---

**Última actualización**: Diciembre 2025

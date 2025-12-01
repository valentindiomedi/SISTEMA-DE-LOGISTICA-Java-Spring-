package com.backend.tpi.api_gateway;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de rutas del API Gateway
 * Define qué URL van a qué microservicio
 */
@Configuration
public class RouteConfig {
	
    @Bean
    public RouteLocator routeLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // Microservicio de Solicitudes (puerto 8083)
                // Maneja solicitudes, clientes y contenedores
                .route("ms-solicitudes", spec -> spec.path("/api/v1/solicitudes/**")
                        .uri("http://ms-solicitudes:8083"))
                .route("ms-clientes", spec -> spec.path("/api/v1/clientes/**")
                        .uri("http://ms-solicitudes:8083"))
                .route("ms-contenedores", spec -> spec.path("/api/v1/contenedores/**")
                        .uri("http://ms-solicitudes:8083"))
                .route("ms-usuarios", spec -> spec.path("/api/v1/usuarios/**")
                        .uri("http://ms-solicitudes:8083"))

                // Microservicio de Gestión y Cálculos (puerto 8081)
                // Maneja tarifas, precios, depósitos y cálculos de distancia
                .route("ms-calculos-gestion", spec -> spec.path("/api/v1/gestion/**")
                        .uri("http://ms-gestion-calculos:8081"))
                .route("ms-calculos-tarifas", spec -> spec.path("/api/v1/tarifas/**")
                        .uri("http://ms-gestion-calculos:8081"))
                .route("ms-calculos-tarifa-volumen-peso", spec -> spec.path("/api/v1/tarifa-volumen-peso/**")
                        .uri("http://ms-gestion-calculos:8081"))
                .route("ms-calculos-precio", spec -> spec.path("/api/v1/precio/**")
                        .uri("http://ms-gestion-calculos:8081"))
                .route("ms-calculos-depositos", spec -> spec.path("/api/v1/depositos/**")
                        .uri("http://ms-gestion-calculos:8081"))

                // Microservicio de Rutas y Transportistas (puerto 8082)
                // Maneja rutas, tramos, camiones y cálculo de rutas con OSRM
                .route("ms-rutas-rutas", spec -> spec.path("/api/v1/rutas/**")
                        .uri("http://ms-rutas-transportistas:8082"))
                .route("ms-rutas-tramos", spec -> spec.path("/api/v1/tramos/**")
                        .uri("http://ms-rutas-transportistas:8082"))
                .route("ms-rutas-osrm", spec -> spec.path("/api/v1/osrm/**")
                        .uri("http://ms-rutas-transportistas:8082"))
                .route("ms-rutas-camiones", spec -> spec.path("/api/v1/camiones/**")
                        .uri("http://ms-rutas-transportistas:8082"))

                // Proxy para Swagger/OpenAPI de los microservicios (no exponer puertos)
                // Accede desde el gateway en /docs/{servicio}/... y reescribe la ruta hacia el servicio interno
                .route("docs-solicitudes", spec -> spec.path("/docs/solicitudes/**")
                        .filters(f -> f.rewritePath("/docs/solicitudes/(?<rem>.*)", "/${rem}"))
                        .uri("http://ms-solicitudes:8083"))
                .route("docs-calculos", spec -> spec.path("/docs/gestion/**")
                        .filters(f -> f.rewritePath("/docs/gestion/(?<rem>.*)", "/${rem}"))
                        .uri("http://ms-gestion-calculos:8081"))
                .route("docs-rutas", spec -> spec.path("/docs/rutas/**")
                        .filters(f -> f.rewritePath("/docs/rutas/(?<rem>.*)", "/${rem}"))
                        .uri("http://ms-rutas-transportistas:8082"))

                .build();
    }

}

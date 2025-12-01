package com.backend.tpi.ms_solicitudes.services;

import com.backend.tpi.ms_solicitudes.dtos.CoordenadaDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

/**
 * Servicio para geocodificación de direcciones
 * Convierte direcciones de texto a coordenadas geográficas
 * utilizando el microservicio ms-gestion-calculos
 */
@Service
public class GeocodificacionService {

    private static final Logger logger = LoggerFactory.getLogger(GeocodificacionService.class);

    @Autowired
    private RestClient calculosClient;

    /**
     * Geocodifica una dirección convirtiéndola a coordenadas
     * Soporta:
     * 1. Coordenadas directas en formato "lat,lon": las retorna parseadas
     * 2. Direcciones de texto: delega la resolución a `ms-gestion-calculos` mediante
     *    el endpoint `/api/v1/gestion/geocode?direccion=...`.
     *
     * @param direccion dirección de texto o coordenadas en formato "lat,lon"
     * @return Coordenadas geográficas, o null si no se puede geocodificar
     */
    public CoordenadaDTO geocodificar(String direccion) {
        if (direccion == null || direccion.trim().isEmpty()) {
            logger.warn("Intento de geocodificar dirección null o vacía");
            return null;
        }
        
        direccion = direccion.trim();
        logger.debug("Geocodificando dirección: {}", direccion);
        
        // 1. Si es un número, es un ID de depósito - delegar a ms-gestion-calculos
        if (esNumeroEntero(direccion)) {
            try {
                logger.debug("Dirección detectada como ID de depósito: {}", direccion);
                return geocodificarViaCalculos(direccion);
            } catch (Exception e) {
                logger.error("Error al geocodificar ID de depósito {}: {}", direccion, e.getMessage());
                return null;
            }
        }
        
        // 2. Si contiene coma, intentar parsear como coordenadas "lat,lon".
        // Si la cadena NO contiene números parseables, no devolvemos error:
        // continuamos la resolución como dirección de texto (external-first).
        if (direccion.contains(",")) {
            try {
                String[] partes = direccion.split(",");
                if (partes.length == 2) {
                    double lat = Double.parseDouble(partes[0].trim());
                    double lon = Double.parseDouble(partes[1].trim());

                    // Validar rangos
                    if (lat < -90 || lat > 90) {
                        logger.debug("Latitud fuera de rango al parsear coordenadas: {}", lat);
                    } else if (lon < -180 || lon > 180) {
                        logger.debug("Longitud fuera de rango al parsear coordenadas: {}", lon);
                    } else {
                        logger.debug("Coordenadas parseadas directamente: lat={}, lon={}", lat, lon);
                        return new CoordenadaDTO(lat, lon);
                    }
                }
            } catch (NumberFormatException e) {
                logger.debug("Cadena con coma pero no es coordenadas numéricas: {} - continuando como texto", direccion);
            }
        }
        
        // 3. Intentar geocodificar como dirección de texto vía ms-gestion-calculos
        try {
            logger.debug("Intentando geocodificar dirección de texto vía ms-gestion-calculos: {}", direccion);
            return geocodificarViaCalculos(direccion);
        } catch (Exception e) {
            logger.error("Error al geocodificar dirección '{}': {}", direccion, e.getMessage());
            return null;
        }
    }

    /**
     * Llama al microservicio ms-gestion-calculos para obtener coordenadas de un depósito
     * Llama al microservicio ms-gestion-calculos para geocodificar una dirección de texto
     * Utiliza el endpoint /api/v1/gestion/geocode?direccion=...
     *
     * @param direccion Dirección de texto o coordenadas en formato "lat,lon"
     * @return Coordenadas obtenidas del servicio
     */
    private CoordenadaDTO geocodificarViaCalculos(String direccion) {
        try {
            String token = extractBearerToken();
            logger.debug("Consultando geocodificación vía ms-gestion-calculos para dirección: {}", direccion);

            org.springframework.http.ResponseEntity<java.util.Map<String, Object>> response = calculosClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/gestion/geocode").queryParam("direccion", direccion).build())
                .headers(h -> { if (token != null) h.setBearerAuth(token); })
                .retrieve()
                .toEntity(new org.springframework.core.ParameterizedTypeReference<java.util.Map<String, Object>>() {});

            java.util.Map<String, Object> responseBody = response != null ? response.getBody() : null;
            if (responseBody != null && responseBody.containsKey("latitud") && responseBody.containsKey("longitud")) {
                Object latObj = responseBody.get("latitud");
                Object lonObj = responseBody.get("longitud");
                Double lat = null;
                Double lon = null;
                if (latObj instanceof Number) lat = ((Number) latObj).doubleValue();
                if (lonObj instanceof Number) lon = ((Number) lonObj).doubleValue();
                if (lat != null && lon != null) {
                    logger.info("Coordenadas obtenidas vía calculos para '{}': lat={}, lon={}", direccion, lat, lon);
                    return new CoordenadaDTO(lat, lon);
                }
            }
            logger.warn("ms-gestion-calculos no devolvió coordenadas para dirección: {} - intentar fallback externo", direccion);
            // Fallback directo desde este servicio hacia Nominatim
            try {
                String q = java.net.URLEncoder.encode(direccion, java.nio.charset.StandardCharsets.UTF_8);
                String url = "https://nominatim.openstreetmap.org/search?q=" + q + "&format=json&limit=1&addressdetails=0";
                java.net.http.HttpClient http = java.net.http.HttpClient.newBuilder().build();
                java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(url))
                        .header("User-Agent", "TPI-Backend-Geocoder/1.0 (contacto@dominio.example)")
                        .GET()
                        .build();
                java.net.http.HttpResponse<String> resp = http.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    String body = resp.body();
                    java.util.regex.Pattern pLat = java.util.regex.Pattern.compile("\\\"lat\\\"\\s*:\\s*\\\"([0-9+\\-\\.]+)\\\"");
                    java.util.regex.Pattern pLon = java.util.regex.Pattern.compile("\\\"lon\\\"\\s*:\\s*\\\"([0-9+\\-\\.]+)\\\"");
                    java.util.regex.Matcher mLat = pLat.matcher(body);
                    java.util.regex.Matcher mLon = pLon.matcher(body);
                    if (mLat.find() && mLon.find()) {
                        double lat = Double.parseDouble(mLat.group(1));
                        double lon = Double.parseDouble(mLon.group(1));
                        logger.info("Geocodificación externa exitosa para '{}': lat={}, lon={}", direccion, lat, lon);
                        return new CoordenadaDTO(lat, lon);
                    }
                } else {
                    logger.debug("Nominatim respondió con status {}", resp.statusCode());
                }
            } catch (Exception e) {
                logger.debug("Error en geocodificación externa desde ms-solicitudes: {}", e.getMessage());
            }
            return null;
        } catch (NumberFormatException e) {
            logger.error("Formato inválido en geocodificación (NumberFormatException) para: {}", direccion);
            throw new RuntimeException("Formato inválido en geocodificación: " + direccion, e);
        } catch (Exception e) {
            logger.error("Error al consultar geocodificación para {}: {}", direccion, e.getMessage());
            throw new RuntimeException("Error al geocodificar: " + direccion, e);
        }
    }

    /**
     * Verifica si una cadena representa un número entero
     * @param str Cadena a verificar
     * @return true si es un número entero válido
     */
    private boolean esNumeroEntero(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        try {
            Long.parseLong(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Método público para que otros servicios puedan verificar si la cadena
     * representa un ID de depósito (número entero). Usado para validar inputs
     * en capas superiores (por ejemplo, evitar pasar depósitos en solicitudes).
     * @param direccion cadena a validar
     * @return true si la cadena parece un ID de depósito
     */
    public boolean isDireccionDeposito(String direccion) {
        return esNumeroEntero(direccion);
    }

    /**
     * Convierte Double a BigDecimal para almacenamiento en base de datos
     * @param valor Valor Double
     * @return BigDecimal equivalente
     */
    public BigDecimal toBigDecimal(Double valor) {
        return valor != null ? BigDecimal.valueOf(valor) : null;
    }

    /**
     * Extrae el token Bearer del SecurityContext si existe
     * @return Token JWT o null
     */
    private String extractBearerToken() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken) {
            return ((JwtAuthenticationToken) auth).getToken().getTokenValue();
        }
        return null;
    }
}

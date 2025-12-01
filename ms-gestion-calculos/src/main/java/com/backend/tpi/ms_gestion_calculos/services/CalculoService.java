package com.backend.tpi.ms_gestion_calculos.services;

import com.backend.tpi.ms_gestion_calculos.dtos.CoordenadaDTO;
// import com.backend.tpi.ms_gestion_calculos.dtos.DepositoDTO; // ya no se usa
import com.backend.tpi.ms_gestion_calculos.dtos.DistanciaRequestDTO;
import com.backend.tpi.ms_gestion_calculos.dtos.DistanciaResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Servicio de negocio para cálculo de Distancias
 * Calcula distancias entre ubicaciones usando OSRM (servicio externo) o fórmula de Haversine como fallback
 */
@Service
public class CalculoService {

    private static final Logger logger = LoggerFactory.getLogger(CalculoService.class);
    
    private static final int RADIO_TIERRA = 6371; // Radio de la Tierra en kilómetros
    
    @Autowired
    private RestClient rutasClient;
    
    // Nota: ya no usamos búsqueda por depósitos en la geocodificación; eliminada.
    
    @Value("${app.osrm.base-url:http://osrm:5000}")
    private String osrmBaseUrl;

    /**
     * Calcula la distancia entre dos ubicaciones
     * Intenta usar OSRM (vía ms-rutas-transportistas) para rutas reales
     * Si falla, usa fórmula de Haversine como fallback
     * @param request Datos de origen y destino
     * @return Distancia en kilómetros y duración estimada (si está disponible)
     */
    public DistanciaResponseDTO calcularDistancia(DistanciaRequestDTO request) {
        logger.debug("Calculando distancia - origen: {}, destino: {}", request.getOrigen(), request.getDestino());
        
        try {
            // Geocodificar origen y destino
            CoordenadaDTO coordOrigen = geocodificar(request.getOrigen());
            CoordenadaDTO coordDestino = geocodificar(request.getDestino());
            
            if (coordOrigen == null || coordDestino == null) {
                logger.warn("No se pudieron geocodificar las direcciones, usando cálculo Haversine");
                double distancia = calcularDistanciaHaversine(request.getOrigen(), request.getDestino());
                logger.info("Distancia calculada (Haversine): {} km", distancia);
                return new DistanciaResponseDTO(distancia, null);
            }
            
            // Llamar a OSRM a través del microservicio de rutas
            logger.debug("Llamando a ms-rutas-transportistas para calcular distancia real con OSRM");
            String token = extractBearerToken();
            DistanciaResponseDTO distanciaResp = rutasClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/osrm/distancia")
                            .queryParam("origenLat", coordOrigen.getLatitud())
                            .queryParam("origenLong", coordOrigen.getLongitud())
                            .queryParam("destinoLat", coordDestino.getLatitud())
                            .queryParam("destinoLong", coordDestino.getLongitud())
                            .build())
                    .headers(h -> { if (token != null) h.setBearerAuth(token); })
                    .retrieve()
                    .body(DistanciaResponseDTO.class);
            
            if (distanciaResp != null && distanciaResp.getDistancia() != null) {
                logger.info("Distancia calculada con OSRM: {} km", distanciaResp.getDistancia());
                return distanciaResp;
            } else {
                logger.warn("OSRM no devolvió resultado válido, usando cálculo Haversine como fallback");
                double distancia = calcularDistanciaHaversine(coordOrigen, coordDestino);
                logger.info("Distancia calculada (Haversine fallback): {} km", distancia);
                return new DistanciaResponseDTO(distancia, null);
            }
            
        } catch (Exception e) {
            logger.error("Error al calcular distancia con OSRM: {}", e.getMessage());
            logger.debug("Stack trace:", e);
            // Fallback a cálculo Haversine en caso de error
            double distancia = calcularDistanciaHaversine(request.getOrigen(), request.getDestino());
            logger.info("Distancia calculada (Haversine por error): {} km", distancia);
            return new DistanciaResponseDTO(distancia, null);
        }
    }

    /**
     * Método público expuesto a controladores para geocodificar una dirección de texto o coordenadas
     * @param direccion texto o coordenadas
     * @return CoordenadaDTO o null si no se pudo geocodificar
     */
    public CoordenadaDTO geocodificarPublic(String direccion) {
        return geocodificar(direccion);
    }
    

    /**
     * Geocodifica una dirección a coordenadas lat/long
     * Soporta solo dos formatos:
     * 1. ID de depósito (número): consulta la base de datos
     * 2. Coordenadas directas en formato "lat,lon"
     * @param direccion ID de depósito o coordenadas
     * @return Coordenadas encontradas, o null si no se encuentra
     * @throws IllegalArgumentException si el formato no es válido
     */
    private CoordenadaDTO geocodificar(String direccion) {
        if (direccion == null || direccion.trim().isEmpty()) {
            logger.error("La dirección no puede ser null o vacía");
            throw new IllegalArgumentException("La dirección es obligatoria");
        }
        
        direccion = direccion.trim();
        // 1. Verificar si son coordenadas en formato "lat,lon"
        if (direccion.contains(",")) {
            try {
                String[] partes = direccion.split(",");
                if (partes.length == 2) {
                    double lat = Double.parseDouble(partes[0].trim());
                    double lon = Double.parseDouble(partes[1].trim());

                    // Validar rangos de coordenadas
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
                // No tratar como error: la dirección contiene comas pero no son coordenadas numéricas.
                logger.debug("Cadena con coma pero no es coordenadas numéricas: {} - continuando como texto", direccion);
            }
        }
        
        // 2. Intentar geocodificación externa (Nominatim) COMO PRIMERA OPCIÓN
        try {
            String q = java.net.URLEncoder.encode(direccion, java.nio.charset.StandardCharsets.UTF_8);
            String url = "https://nominatim.openstreetmap.org/search?q=" + q + "&format=json&limit=1&addressdetails=0";
            logger.debug("Intentando geocodificación externa (Nominatim) para: {}", direccion);

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
            logger.debug("Error en geocodificación externa: {}", e.getMessage());
        }

        // 3. Si la geocodificación externa no devuelve resultado, aplicar SOLO
        // heurística por nombre de ciudad como fallback.
        String lower = direccion.toLowerCase();
        if (lower.contains("caba") || lower.contains("buenos") || lower.contains("b.a.s.a")) {
            logger.info("Heurística por ciudad aplicada: CABA para '{}'", direccion);
            return new CoordenadaDTO(-34.6037, -58.3816);
        }
        if (lower.contains("rosario")) {
            logger.info("Heurística por ciudad aplicada: Rosario para '{}'", direccion);
            return new CoordenadaDTO(-32.9445, -60.6500);
        }
        if (lower.contains("cordoba")) {
            logger.info("Heurística por ciudad aplicada: Córdoba para '{}'", direccion);
            return new CoordenadaDTO(-31.4167, -64.1833);
        }
        if (lower.contains("mendoza")) {
            logger.info("Heurística por ciudad aplicada: Mendoza para '{}'", direccion);
            return new CoordenadaDTO(-32.8908, -68.8272);
        }

        logger.warn("No se pudo geocodificar la dirección de texto (sin resultados externos ni heurística de ciudad): {}", direccion);
        return null;
    }
    
    // Nota: ya no soportamos IDs de depósito como entrada directa para geocodificación
    // (la lógica que buscaba por ID fue retirada en favor de búsqueda por dirección/texto).

    /**
     * Calcula distancia usando fórmula de Haversine entre dos ciudades
     * Geocodifica los nombres de las ciudades primero
     * @param origen Nombre de la ciudad origen
     * @param destino Nombre de la ciudad destino
     * @return Distancia en kilómetros (redondeada a 2 decimales)
     */
    private double calcularDistanciaHaversine(String origen, String destino) {
        logger.debug("Aplicando fórmula de Haversine para calcular distancia");
        
        // Intentar geocodificar
        CoordenadaDTO coordOrigen = geocodificar(origen);
        CoordenadaDTO coordDestino = geocodificar(destino);
        
        if (coordOrigen != null && coordDestino != null) {
            return calcularDistanciaHaversine(coordOrigen, coordDestino);
        }
        
        // Si no se obtuvieron coordenadas para origen/destino, no aplicamos
        // una suposición mediante ciudades por defecto. Devolvemos 0.0 para
        // indicar que no se pudo estimar la distancia con la información dada.
        logger.warn("No se pudieron geocodificar origen o destino para Haversine; devolviendo distancia 0.0");
        return 0.0;
    }
    
    /**
     * Calcula distancia usando fórmula de Haversine entre dos coordenadas
     * Fórmula matemática para calcular distancia en la superficie de una esfera
     * @param origen Coordenadas del punto origen
     * @param destino Coordenadas del punto destino
     * @return Distancia en kilómetros (redondeada a 2 decimales)
     */
    private double calcularDistanciaHaversine(CoordenadaDTO origen, CoordenadaDTO destino) {
        double lat1 = origen.getLatitud();
        double lon1 = origen.getLongitud();
        double lat2 = destino.getLatitud();
        double lon2 = destino.getLongitud();

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return Math.round(RADIO_TIERRA * c * 100.0) / 100.0; // Redondear a 2 decimales
    }

    /**
     * Helper: extrae token Bearer del SecurityContext si existe
     */
    private String extractBearerToken() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken) {
            return ((JwtAuthenticationToken) auth).getToken().getTokenValue();
        }
        return null;
    }
}

package com.backend.tpi.ms_rutas_transportistas.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.*;

@Service
public class DepositoService {

    private static final Logger logger = LoggerFactory.getLogger(DepositoService.class);

    @Autowired
    private RestClient calculosClient;

    /**
     * Obtiene la lista completa de depósitos (mapas con keys como id, latitud, longitud, nombre, etc.)
     */
    public List<Map<String, Object>> getAllDepositos() {
        try {
            String token = extractBearerToken();
            ResponseEntity<List<Map<String, Object>>> resp = calculosClient.get()
                    .uri("/api/v1/depositos")
                    .headers(h -> { if (token != null) h.setBearerAuth(token); })
                    .retrieve()
                    .toEntity(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            return resp.getBody() != null ? resp.getBody() : Collections.emptyList();
        } catch (Exception e) {
            logger.warn("Error al obtener lista de depósitos desde ms-gestion-calculos: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Obtiene info para un conjunto de depósitos usando el endpoint /depositos/{id}/coordenadas
     */
    public Map<Long, Map<String, Object>> getInfoForDepositos(List<Long> depositosIds) {
        logger.info("=== INICIO getInfoForDepositos ===");
        logger.info("Solicitando info para depósitos: {}", depositosIds);
        Map<Long, Map<String, Object>> resultado = new HashMap<>();
        if (depositosIds == null || depositosIds.isEmpty()) return resultado;
        String token = extractBearerToken();
        logger.info("Token extraído: {}", token != null ? "PRESENTE (longitud=" + token.length() + ")" : "AUSENTE");
        for (Long id : depositosIds) {
            try {
                logger.info("Llamando GET /api/v1/depositos/{}/coordenadas", id);
                ResponseEntity<Map<String, Object>> resp = calculosClient.get()
                        .uri("/api/v1/depositos/{id}/coordenadas", id)
                        .headers(h -> { if (token != null) h.setBearerAuth(token); })
                        .retrieve()
                        .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {});
                if (resp.getBody() != null) {
                    resultado.put(id, resp.getBody());
                    logger.info("Depósito {} obtenido: {}", id, resp.getBody());
                } else {
                    logger.warn("Depósito {} respondió con body null", id);
                }
            } catch (Exception e) {
                logger.error("Error al obtener info del depósito {}: {} - {}", id, e.getClass().getSimpleName(), e.getMessage());
            }
        }
        logger.info("=== FIN getInfoForDepositos: {} depósitos obtenidos ===", resultado.size());
        return resultado;
    }

    /**
     * Devuelve hasta k depósitos (IDs) más cercanos al segmento definido por origen-destino,
     * excluyendo origenId y destinoId.
     */
    public List<Long> getKNearestToRoute(Long origenId, Long destinoId, int k) {
        List<Map<String, Object>> all = getAllDepositos();
        if (all.isEmpty()) return Collections.emptyList();

        Map<Long, Map<String, Object>> mapById = new HashMap<>();
        for (Map<String, Object> d : all) {
            Object idObj = d.get("id");
            if (idObj instanceof Number) mapById.put(((Number) idObj).longValue(), d);
        }

        Map<String, Object> origen = mapById.get(origenId);
        Map<String, Object> destino = mapById.get(destinoId);
        if (origen == null || destino == null) {
            // fallback: return first k excluding origen/destino
            List<Long> fallback = new ArrayList<>();
            for (Map<String, Object> d : all) {
                Long id = d.get("id") instanceof Number ? ((Number) d.get("id")).longValue() : null;
                if (id == null) continue;
                if (id.equals(origenId) || id.equals(destinoId)) continue;
                fallback.add(id);
                if (fallback.size() >= k) break;
            }
            return fallback;
        }

        double orLat = ((Number) origen.get("latitud")).doubleValue();
        double orLon = ((Number) origen.get("longitud")).doubleValue();
        double deLat = ((Number) destino.get("latitud")).doubleValue();
        double deLon = ((Number) destino.get("longitud")).doubleValue();

        // Compute distance from each deposit to the segment (approx using equirectangular projection in km)
        List<Map.Entry<Long, Double>> distances = new ArrayList<>();
        for (Map.Entry<Long, Map<String, Object>> e : mapById.entrySet()) {
            Long id = e.getKey();
            if (id.equals(origenId) || id.equals(destinoId)) continue;
            Map<String, Object> info = e.getValue();
            if (info.get("latitud") == null || info.get("longitud") == null) continue;
            double lat = ((Number) info.get("latitud")).doubleValue();
            double lon = ((Number) info.get("longitud")).doubleValue();
            double dist = pointToSegmentDistanceKm(orLat, orLon, deLat, deLon, lat, lon);
            distances.add(new AbstractMap.SimpleEntry<>(id, dist));
        }

        distances.sort(Comparator.comparingDouble(Map.Entry::getValue));
        List<Long> result = new ArrayList<>();
        for (int i = 0; i < Math.min(k, distances.size()); i++) result.add(distances.get(i).getKey());
        return result;
    }

    // Approximate distance from point P to segment AB in kilometers using equirectangular projection
    private double pointToSegmentDistanceKm(double aLat, double aLon, double bLat, double bLon, double pLat, double pLon) {
        // convert degrees to radians
        double lat1 = Math.toRadians(aLat);
        double lon1 = Math.toRadians(aLon);
        double lat2 = Math.toRadians(bLat);
        double lon2 = Math.toRadians(bLon);
        double latP = Math.toRadians(pLat);
        double lonP = Math.toRadians(pLon);

        double meanLat = (lat1 + lat2 + latP) / 3.0;
        double kmPerDegLat = 111.32; // approx
        double kmPerDegLon = 111.32 * Math.cos(meanLat);

        double ax = aLon * kmPerDegLon;
        double ay = aLat * kmPerDegLat;
        double bx = bLon * kmPerDegLon;
        double by = bLat * kmPerDegLat;
        double px = pLon * kmPerDegLon;
        double py = pLat * kmPerDegLat;

        double vx = bx - ax;
        double vy = by - ay;
        double wx = px - ax;
        double wy = py - ay;

        double c1 = vx * wx + vy * wy;
        double c2 = vx * vx + vy * vy;
        double t = c2 == 0 ? 0 : c1 / c2;
        if (t < 0) t = 0; if (t > 1) t = 1;
        double projx = ax + t * vx;
        double projy = ay + t * vy;
        double dx = px - projx;
        double dy = py - projy;
        return Math.sqrt(dx*dx + dy*dy);
    }

    private String extractBearerToken() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken) {
            return ((org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken) auth).getToken().getTokenValue();
        }
        return null;
    }
}

package com.backend.tpi.ms_rutas_transportistas.services;

import com.backend.tpi.ms_rutas_transportistas.dtos.osrm.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Servicio para integrar con OSRM (Open Source Routing Machine)
 * Calcula rutas reales usando datos de OpenStreetMap
 * OSRM provee cálculos de distancia y tiempo más precisos que la fórmula Haversine
 */
@Service
@Slf4j
public class OSRMService {

    @Value("${app.osrm.base-url:http://osrm:5000}")
    private String osrmBaseUrl;

    private final RestClient restClient;

    public OSRMService(@Value("${app.osrm.base-url:http://osrm:5000}") String osrmBaseUrl) {
        this.osrmBaseUrl = osrmBaseUrl;
        this.restClient = RestClient.builder()
                .baseUrl(osrmBaseUrl)
                .build();
    }

    /**
     * Calcula la ruta entre dos coordenadas usando OSRM
     * @param origen Coordenada de origen (lat, lon)
     * @param destino Coordenada de destino (lat, lon)
     * @return RutaCalculadaDTO con distancia, duración y geometría
     */
    public RutaCalculadaDTO calcularRuta(CoordenadaDTO origen, CoordenadaDTO destino) {
        try {
            // OSRM usa formato: /route/v1/{profile}/{coordinates}
            // Coordenadas en formato: lon,lat;lon,lat
            String coordinates = String.format("%f,%f;%f,%f",
                    origen.getLongitud(), origen.getLatitud(),
                    destino.getLongitud(), destino.getLatitud());

            // Construir URL directamente para evitar problemas de encoding con ; y ,
            String uri = String.format("/route/v1/driving/%s?overview=full&steps=true&geometries=polyline", 
                    coordinates);

            log.info("Llamando a OSRM: {}", osrmBaseUrl + uri);

            OSRMRouteResponse response = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(OSRMRouteResponse.class);

            if (response == null || !"Ok".equals(response.getCode()) || response.getRoutes().isEmpty()) {
                return RutaCalculadaDTO.builder()
                        .exitoso(false)
                        .mensaje("No se pudo calcular la ruta. Código: " + (response != null ? response.getCode() : "null"))
                        .build();
            }

            OSRMRoute route = response.getRoutes().get(0);
            
            // Convertir metros a km, segundos a horas y minutos
            double distanciaKm = route.getDistance() / 1000.0;
            double duracionHoras = route.getDuration() / 3600.0;
            double duracionMinutos = route.getDuration() / 60.0;

            String resumen = route.getLegs() != null && !route.getLegs().isEmpty() 
                    ? route.getLegs().get(0).getSummary() 
                    : "Ruta calculada";

            return RutaCalculadaDTO.builder()
                    .exitoso(true)
                    .distanciaKm(Math.round(distanciaKm * 100.0) / 100.0)  // 2 decimales
                    .duracionHoras(Math.round(duracionHoras * 100.0) / 100.0)
                    .duracionMinutos(Math.round(duracionMinutos * 100.0) / 100.0)
                    .geometry(route.getGeometry())
                    .resumen(resumen)
                    .mensaje("Ruta calculada exitosamente")
                    .build();

        } catch (Exception e) {
            log.error("Error al calcular ruta con OSRM", e);
            return RutaCalculadaDTO.builder()
                    .exitoso(false)
                    .mensaje("Error al calcular ruta: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Calcula la ruta entre múltiples puntos (waypoints)
     * @param coordenadas Lista de coordenadas a visitar
     * @return RutaCalculadaDTO con la ruta completa
     */
    public RutaCalculadaDTO calcularRutaMultiple(CoordenadaDTO... coordenadas) {
        if (coordenadas == null || coordenadas.length < 2) {
            return RutaCalculadaDTO.builder()
                    .exitoso(false)
                    .mensaje("Se requieren al menos 2 coordenadas")
                    .build();
        }

        try {
            // Construir string de coordenadas: lon,lat;lon,lat;...
            StringBuilder coordinates = new StringBuilder();
            for (int i = 0; i < coordenadas.length; i++) {
                if (i > 0) coordinates.append(";");
                coordinates.append(String.format("%f,%f",
                        coordenadas[i].getLongitud(),
                        coordenadas[i].getLatitud()));
            }

            String uri = String.format("/route/v1/driving/%s?overview=full&steps=true&geometries=polyline",
                    coordinates.toString());

            log.info("Llamando a OSRM con {} waypoints: {}", coordenadas.length, osrmBaseUrl + uri);

            OSRMRouteResponse response = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(OSRMRouteResponse.class);

            if (response == null || !"Ok".equals(response.getCode()) || response.getRoutes().isEmpty()) {
                return RutaCalculadaDTO.builder()
                        .exitoso(false)
                        .mensaje("No se pudo calcular la ruta múltiple")
                        .build();
            }

            OSRMRoute route = response.getRoutes().get(0);
            
            double distanciaKm = route.getDistance() / 1000.0;
            double duracionHoras = route.getDuration() / 3600.0;
            double duracionMinutos = route.getDuration() / 60.0;

            return RutaCalculadaDTO.builder()
                    .exitoso(true)
                    .distanciaKm(Math.round(distanciaKm * 100.0) / 100.0)
                    .duracionHoras(Math.round(duracionHoras * 100.0) / 100.0)
                    .duracionMinutos(Math.round(duracionMinutos * 100.0) / 100.0)
                    .geometry(route.getGeometry())
                    .resumen(coordenadas.length + " puntos visitados")
                    .mensaje("Ruta calculada exitosamente")
                    .build();

        } catch (Exception e) {
            log.error("Error al calcular ruta múltiple con OSRM", e);
            return RutaCalculadaDTO.builder()
                    .exitoso(false)
                    .mensaje("Error al calcular ruta: " + e.getMessage())
                    .build();
        }
    }
}

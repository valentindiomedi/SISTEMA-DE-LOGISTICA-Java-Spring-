package com.backend.tpi.ms_gestion_calculos.services;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

/**
 * Servicio para integración con Nominatim (OpenStreetMap Geocoding)
 * Permite obtener información geográfica a partir de coordenadas (reverse geocoding)
 * Nominatim es el servicio de geocodificación gratuito de OpenStreetMap
 */
@Service
@Slf4j
public class NominatimService {

    @Value("${app.nominatim.base-url:https://nominatim.openstreetmap.org}")
    private String nominatimBaseUrl;

    private final RestClient restClient;

    public NominatimService(@Value("${app.nominatim.base-url:https://nominatim.openstreetmap.org}") String nominatimBaseUrl) {
        this.nominatimBaseUrl = nominatimBaseUrl;
        this.restClient = RestClient.builder()
                .baseUrl(nominatimBaseUrl)
                .build();
    }

    /**
     * Obtiene información de ubicación a partir de coordenadas geográficas
     * Este método incluye protección contra bloqueos de Nominatim:
     * - User-Agent descriptivo con información del proyecto
     * - Delay de 1 segundo entre peticiones (política de Nominatim)
     * - Manejo robusto de errores
     * 
     * @param latitud Latitud del punto
     * @param longitud Longitud del punto
     * @return Información de ubicación (ciudad, provincia, país, etc.), null si falla
     */
    public UbicacionDTO obtenerUbicacion(BigDecimal latitud, BigDecimal longitud) {
        try {
            // Nominatim requiere máximo 1 request por segundo
            // Agregamos un pequeño delay para cumplir con la política de uso
            Thread.sleep(1100);
            
            log.info("Consultando Nominatim para coordenadas: lat={}, lon={}", latitud, longitud);
            
            // Nominatim reverse endpoint: /reverse?lat={lat}&lon={lon}&format=json
            // Agregamos User-Agent requerido por las políticas de uso de Nominatim
            // IMPORTANTE: Nominatim requiere un User-Agent descriptivo con información de contacto
            // Formato: <Application Name>/<Version> (<Contact Information>)
            NominatimResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/reverse")
                            .queryParam("lat", latitud.toString())
                            .queryParam("lon", longitud.toString())
                            .queryParam("format", "json")
                            .queryParam("addressdetails", "1")
                            .queryParam("zoom", "10") // Nivel de zoom para obtener ciudad
                            .build())
                    .header("User-Agent", "TPI-Backend-GestionCalculos/1.0 (+https://github.com/BautiMelo/TPI-Backend)")
                    .header("Referer", "https://github.com/BautiMelo/TPI-Backend")
                    .retrieve()
                    .body(NominatimResponse.class);

            if (response == null || response.getAddress() == null) {
                log.warn("No se obtuvo respuesta válida de Nominatim para lat={}, lon={}", latitud, longitud);
                return null;
            }

            log.info("Ubicación encontrada: {}", response.getDisplayName());

            UbicacionDTO ubicacion = new UbicacionDTO();
            ubicacion.setCiudad(extraerNombreCiudad(response.getAddress()));
            ubicacion.setProvincia(response.getAddress().getState());
            ubicacion.setPais(response.getAddress().getCountry());
            ubicacion.setDireccionCompleta(response.getDisplayName());
            
            return ubicacion;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupción durante delay de Nominatim", e);
            return null;
        } catch (org.springframework.web.client.HttpClientErrorException.Forbidden e) {
            log.error("Acceso bloqueado por Nominatim. Esto puede deberse a: " +
                "1) Demasiadas peticiones (máx 1/segundo), " +
                "2) User-Agent no reconocido, " +
                "3) Bloqueo temporal por uso excesivo. " +
                "Se recomienda usar 'nombreCiudad' manualmente en el DTO para evitar este problema.", e);
            return null;
        } catch (Exception e) {
            log.error("Error al consultar Nominatim para lat={}, lon={}. " +
                "Recomendación: especificar 'nombreCiudad' manualmente en el request.", 
                latitud, longitud, e);
            return null;
        }
    }

    /**
     * Extrae el nombre de la ciudad de la respuesta de Nominatim
     * Nominatim puede devolver la ciudad en diferentes campos dependiendo del tipo de ubicación
     */
    private String extraerNombreCiudad(NominatimAddress address) {
        // Nominatim puede poner la ciudad en varios campos dependiendo del contexto
        if (address.getCity() != null && !address.getCity().isEmpty()) {
            return address.getCity();
        }
        if (address.getTown() != null && !address.getTown().isEmpty()) {
            return address.getTown();
        }
        if (address.getVillage() != null && !address.getVillage().isEmpty()) {
            return address.getVillage();
        }
        if (address.getMunicipality() != null && !address.getMunicipality().isEmpty()) {
            return address.getMunicipality();
        }
        if (address.getCounty() != null && !address.getCounty().isEmpty()) {
            return address.getCounty();
        }
        
        // Si no hay nada específico, usar el estado/provincia como fallback
        return address.getState();
    }

    /**
     * DTO para la respuesta de Nominatim
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class NominatimResponse {
        @JsonProperty("place_id")
        private Long placeId;
        
        @JsonProperty("display_name")
        private String displayName;
        
        private NominatimAddress address;
    }

    /**
     * DTO para la dirección en la respuesta de Nominatim
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class NominatimAddress {
        private String city;           // Ciudad
        private String town;           // Pueblo
        private String village;        // Villa
        private String municipality;   // Municipio
        private String county;         // Condado/Partido
        private String state;          // Provincia/Estado
        
        @JsonProperty("state_district")
        private String stateDistrict;  // Distrito de estado
        
        private String country;        // País
        
        @JsonProperty("country_code")
        private String countryCode;    // Código de país (ar, br, etc.)
    }

    /**
     * DTO para información de ubicación
     */
    @Data
    public static class UbicacionDTO {
        private String ciudad;
        private String provincia;
        private String pais;
        private String direccionCompleta;
    }
}

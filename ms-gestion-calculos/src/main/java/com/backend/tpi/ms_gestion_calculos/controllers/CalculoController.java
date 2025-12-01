package com.backend.tpi.ms_gestion_calculos.controllers;

import com.backend.tpi.ms_gestion_calculos.dtos.DistanciaRequestDTO;
import com.backend.tpi.ms_gestion_calculos.dtos.DistanciaResponseDTO;
import com.backend.tpi.ms_gestion_calculos.services.CalculoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST para cálculos de distancia
 * Expone endpoints para calcular distancias entre orígenes y destinos
 * Usa OSRM para cálculos reales o Haversine como fallback
 */
@RestController
@RequestMapping("/api/v1/gestion")
public class CalculoController {

    private static final Logger logger = LoggerFactory.getLogger(CalculoController.class);

    @Autowired
    private CalculoService calculoService;

    /**
    * POST /api/v1/gestion/distancia - Calcula la distancia entre dos ubicaciones
    * Requiere rol CLIENTE u OPERADOR
     * @param request Datos de origen y destino
     * @return Distancia en kilómetros y tiempo estimado
     */
    @PostMapping("/distancia")
    @PreAuthorize("hasAnyRole('CLIENTE','OPERADOR')")
    public ResponseEntity<DistanciaResponseDTO> calcularDistancia(@RequestBody DistanciaRequestDTO request) {
        logger.info("POST /api/v1/gestion/distancia - Calculando distancia");
        DistanciaResponseDTO result = calculoService.calcularDistancia(request);
        logger.info("POST /api/v1/gestion/distancia - Respuesta: 200 - Distancia: {} km", result.getDistancia());
        return ResponseEntity.ok(result);
    }

    /**
    * GET /api/v1/gestion/geocode?direccion=... - Geocodifica una dirección de texto o coordenadas
    * Requiere rol CLIENTE u OPERADOR
     */
    @GetMapping("/geocode")
    @PreAuthorize("hasAnyRole('CLIENTE','OPERADOR')")
    public ResponseEntity<com.backend.tpi.ms_gestion_calculos.dtos.CoordenadaDTO> geocode(@RequestParam String direccion) {
        logger.info("GET /api/v1/gestion/geocode - Geocodificando: {}", direccion);
        com.backend.tpi.ms_gestion_calculos.dtos.CoordenadaDTO coord = calculoService.geocodificarPublic(direccion);
        if (coord == null) {
            logger.warn("No se pudo geocodificar la dirección: {}", direccion);
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(coord);
    }
}

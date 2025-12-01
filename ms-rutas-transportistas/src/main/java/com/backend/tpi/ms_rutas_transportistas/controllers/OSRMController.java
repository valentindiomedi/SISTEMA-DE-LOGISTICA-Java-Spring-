package com.backend.tpi.ms_rutas_transportistas.controllers;

import com.backend.tpi.ms_rutas_transportistas.dtos.osrm.CoordenadaDTO;
import com.backend.tpi.ms_rutas_transportistas.dtos.osrm.RutaCalculadaDTO;
import com.backend.tpi.ms_rutas_transportistas.services.OSRMService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

 

/**
 * Controlador REST para cálculos de rutas con OSRM
 * Expone endpoints para calcular distancias y rutas reales entre coordenadas
 * Utiliza el servicio OSRM que se conecta al servidor OSRM en Docker
 */
@RestController
@RequestMapping("/api/v1/osrm")
@Tag(name = "OSRM", description = "Cálculo de rutas y distancias usando OSRM")
@Slf4j
public class OSRMController {

    @Autowired
    private OSRMService osrmService;


    /**
     * Calcula la distancia y duración entre dos puntos (endpoint de compatibilidad)
     * @param origenLat Latitud del punto de origen
     * @param origenLong Longitud del punto de origen
     * @param destinoLat Latitud del punto de destino
     * @param destinoLong Longitud del punto de destino
     * @return Distancia en km y duración en minutos
     */
    @GetMapping("/distancia")
    @PreAuthorize("hasAnyRole('CLIENTE','OPERADOR','ADMIN')")
    @Operation(summary = "Calcular distancia y duración entre dos puntos",
            description = "Calcula distancia usando OSRM - Compatible con endpoint legacy /maps/distancia")
    public ResponseEntity<com.backend.tpi.ms_rutas_transportistas.dtos.DistanciaResponseDTO> getDistancia(
            @RequestParam Double origenLat,
            @RequestParam Double origenLong,
            @RequestParam Double destinoLat,
            @RequestParam Double destinoLong) {
        
        CoordenadaDTO origen = new CoordenadaDTO(origenLat, origenLong);
        CoordenadaDTO destino = new CoordenadaDTO(destinoLat, destinoLong);
        
        RutaCalculadaDTO resultado = osrmService.calcularRuta(origen, destino);
        
        // Convertir a DistanciaResponseDTO para compatibilidad
        com.backend.tpi.ms_rutas_transportistas.dtos.DistanciaResponseDTO response = 
            new com.backend.tpi.ms_rutas_transportistas.dtos.DistanciaResponseDTO();
        response.setDistancia(resultado.getDistanciaKm());
        response.setDuracion(resultado.getDuracionMinutos());
        
        return ResponseEntity.ok(response);
    }

    // Only /distancia endpoint is kept for external use; other route calculation
    // capabilities are available internally via the OSRMService bean.
}

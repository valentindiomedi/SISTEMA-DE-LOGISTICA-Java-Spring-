package com.backend.tpi.ms_rutas_transportistas.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO de salida para opciones de ruta (legible por cliente)
 * - No incluye la geometría
 * - Deserializa los campos JSON que estaban en la entidad
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RutaOpcionDTO {
    private Long id;
    private Long rutaId;
    private Long solicitudId;
    private Integer opcionIndex;
    private Double distanciaTotal;
    private Double duracionTotalHoras;
    private Double costoTotal;

    // Campos deserializados
    private List<Long> depositosIds;
    private List<String> depositosNombres;
    private List<TramoTentativoDTO> tramos;
    /**
     * Representación legible por humanos: resumen corto de la opción
     * Ej: "Opción 1: 340.85 km, 3.95 h, 3 tramos"
     */
    private String resumen;

    /**
     * Lista con una línea descriptiva por tramo
     * Ej: "Tramo 1: Punto de Origen -> Depósito Retiro (4.71 km, 0.13 h)"
     */
    private List<String> resumenTramos;
    
    private LocalDateTime fechaCreacion;
}

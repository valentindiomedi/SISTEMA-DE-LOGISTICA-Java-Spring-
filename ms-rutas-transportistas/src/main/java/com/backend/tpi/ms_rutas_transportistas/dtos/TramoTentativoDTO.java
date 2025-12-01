package com.backend.tpi.ms_rutas_transportistas.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para representar un tramo individual en una ruta tentativa
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TramoTentativoDTO {
    /**
     * ID del depósito origen
     */
    private Long origenDepositoId;
    
    /**
     * Nombre del depósito origen
     */
    private String origenDepositoNombre;
    
    /**
     * ID del depósito destino
     */
    private Long destinoDepositoId;
    
    /**
     * Nombre del depósito destino
     */
    private String destinoDepositoNombre;
    
    /**
     * Distancia del tramo en kilómetros
     */
    private Double distanciaKm;
    
    /**
     * Duración estimada del tramo en horas
     */
    private Double duracionHoras;
    
    /**
     * Costo aproximado del tramo
     */
    private Double costoAproximado;
    
    /**
     * Orden del tramo en la ruta (1, 2, 3, ...)
     */
    private Integer orden;
    
    /**
     * Coordenadas del punto de origen (para tramos que no inician en un depósito)
     */
    private Double origenLat;
    private Double origenLong;
    
    /**
     * Coordenadas del punto de destino (para tramos que no terminan en un depósito)
     */
    private Double destinoLat;
    private Double destinoLong;
}

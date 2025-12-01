package com.backend.tpi.ms_rutas_transportistas.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO para representar una ruta tentativa calculada
 * Incluye los depósitos intermedios y métricas de la ruta
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RutaTentativaDTO {
    /**
     * Lista ordenada de IDs de depósitos que conforman la ruta
     * [origenDepositoId, deposito1, deposito2, ..., destinoDepositoId]
     */
    private List<Long> depositosIds;
    
    /**
     * Lista con nombres de los depósitos para facilitar visualización
     */
    private List<String> depositosNombres;
    
    /**
     * Distancia total de la ruta en kilómetros
     */
    private Double distanciaTotal;
    
    /**
     * Duración total estimada en horas
     */
    private Double duracionTotalHoras;
    
    /**
     * Costo total aproximado de la ruta
     */
    private Double costoTotal;
    
    /**
     * Número de tramos que componen la ruta
     */
    private Integer numeroTramos;
    
    /**
     * Detalles de cada tramo individual
     */
    private List<TramoTentativoDTO> tramos;
    
    /**
     * Geometría de la ruta completa en formato polyline (para visualización en mapas)
     */
    private String geometry;
    
    /**
     * Indica si la ruta fue calculada exitosamente
     */
    private Boolean exitoso;
    
    /**
     * Mensaje descriptivo (éxito o error)
     */
    private String mensaje;
}

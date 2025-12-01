package com.backend.tpi.ms_rutas_transportistas.dtos;

import lombok.Data;
import java.util.List;

/**
 * DTO para crear una nueva ruta
 * Incluye depósitos para cálculo automático de tramos
 */
@Data
public class CreateRutaDTO {
    /**
     * ID de la solicitud asociada (obligatorio)
     */
    private Long idSolicitud;
    
    /**
     * ID del depósito origen (obligatorio)
     */
    private Long origenDepositoId;
    
    /**
     * ID del depósito destino (obligatorio)
     */
    private Long destinoDepositoId;
    
    /**
     * IDs de depósitos intermedios en orden (opcional)
     * Si no se especifica, se calculará la ruta óptima
     */
    private List<Long> depositosIntermediosIds;
    
    /**
     * Si es true, calcula múltiples rutas y elige la más corta (opcional)
     * Por defecto: true
     */
    private Boolean calcularRutaOptima = true;
}

package com.backend.tpi.ms_solicitudes.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para representar coordenadas geográficas (latitud y longitud)
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CoordenadaDTO {
    private Double latitud;
    private Double longitud;
}

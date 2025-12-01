package com.backend.tpi.ms_rutas_transportistas.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DistanciaResponseDTO {
    private Double distancia;  // en kilómetros
    private Double duracion;   // en horas (opcional)
}

package com.backend.tpi.ms_solicitudes.dtos;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CostoResponseDTO {
    private Double costoTotal;
    private String tiempoEstimado;
}

package com.backend.tpi.ms_gestion_calculos.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DistanciaResponseDTO {
    private Double distancia;
    private Double duracion; // En minutos
}

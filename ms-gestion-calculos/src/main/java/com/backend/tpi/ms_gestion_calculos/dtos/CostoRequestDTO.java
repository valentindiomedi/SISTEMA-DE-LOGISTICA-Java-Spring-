package com.backend.tpi.ms_gestion_calculos.dtos;

import lombok.Data;

@Data
public class CostoRequestDTO {
    private String origen;
    private String destino;
    private Double peso;
    private Double volumen;
}

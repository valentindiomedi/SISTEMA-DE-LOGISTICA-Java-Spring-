package com.backend.tpi.ms_rutas_transportistas.dtos;

import lombok.Data;

@Data
public class CamionDTO {
    private Long id;
    private String dominio; // Patente/dominio
    private String marca;
    private String modelo;
    private Double capacidadPesoMax;
    private Double capacidadVolumenMax;
    private String nombreTransportista;
    private Double costoBase;
    private Double costoPorKm;
    private Double consumoCombustiblePromedio;
    private Integer numeroTransportistas;
    private Boolean disponible; // Estado operativo
    private Boolean activo; // Activo en sistema
}

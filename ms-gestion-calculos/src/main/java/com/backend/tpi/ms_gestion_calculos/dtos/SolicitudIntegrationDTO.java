package com.backend.tpi.ms_gestion_calculos.dtos;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SolicitudIntegrationDTO {
    private Long id;
    private String nombre;
    private String direccionOrigen;
    private String direccionDestino;
    private String estado;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaModificacion;
    // Nota: el microservicio ms-solicitudes no expone peso/volumen en su DTO actual.
}

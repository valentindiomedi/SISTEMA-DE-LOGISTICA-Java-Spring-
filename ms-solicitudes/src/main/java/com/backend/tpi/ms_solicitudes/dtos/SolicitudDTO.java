package com.backend.tpi.ms_solicitudes.dtos;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SolicitudDTO {
    private Long id;
    private Long clienteId;
    private String direccionOrigen;
    private String direccionDestino;
    private Long contenedorId;
    // Coordenadas (si están geocodificadas)
    private Double origenLat;
    private Double origenLong;
    private Double destinoLat;
    private Double destinoLong;
    private String estado;
    private Long rutaId;
    private Long tarifaId;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaModificacion;
}

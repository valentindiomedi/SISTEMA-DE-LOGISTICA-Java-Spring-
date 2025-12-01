package com.backend.tpi.ms_solicitudes.dtos;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SeguimientoSolicitudDTO {
    // Información de la solicitud
    private Long solicitudId;
    private String estadoSolicitud;
    private Long rutaId;
    private Double origenLat;
    private Double origenLong;
    private Double destinoLat;
    private Double destinoLong;
    
    // Información del contenedor (si existe)
    private Long contenedorId;
    private String estadoContenedor;
    private BigDecimal ubicacionActualLat;
    private BigDecimal ubicacionActualLong;
    private Long depositoId;
}

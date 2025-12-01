package com.backend.tpi.ms_solicitudes.dtos;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SeguimientoContenedorDTO {
    private Long idContenedor;
    private String estadoActual;
    private BigDecimal ubicacionActualLat;
    private BigDecimal ubicacionActualLong;
    private Long depositoId;
}

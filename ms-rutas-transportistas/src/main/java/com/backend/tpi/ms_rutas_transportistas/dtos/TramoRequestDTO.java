package com.backend.tpi.ms_rutas_transportistas.dtos;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TramoRequestDTO {
    private Long idRuta;
    private Long origenDepositoId;
    private Long destinoDepositoId;
    private BigDecimal origenLat;
    private BigDecimal origenLong;
    private BigDecimal destinoLat;
    private BigDecimal destinoLong;
}

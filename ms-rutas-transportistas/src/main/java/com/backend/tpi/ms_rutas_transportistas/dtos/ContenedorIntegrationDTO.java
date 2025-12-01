package com.backend.tpi.ms_rutas_transportistas.dtos;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ContenedorIntegrationDTO {
    private Long id;
    private BigDecimal peso;
    private BigDecimal volumen;
}

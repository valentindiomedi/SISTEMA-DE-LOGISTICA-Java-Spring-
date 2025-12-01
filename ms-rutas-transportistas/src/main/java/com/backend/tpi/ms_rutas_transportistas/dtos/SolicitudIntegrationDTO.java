package com.backend.tpi.ms_rutas_transportistas.dtos;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SolicitudIntegrationDTO {
    private Long id;
    private Long contenedorId;
    private LocalDateTime fechaCreacion;
}

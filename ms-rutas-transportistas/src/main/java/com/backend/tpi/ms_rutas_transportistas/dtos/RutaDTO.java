package com.backend.tpi.ms_rutas_transportistas.dtos;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class RutaDTO {
    private Long id;
    private Long idSolicitud;
    private LocalDateTime fechaCreacion;
    private Long opcionSeleccionadaId;
    private List<TramoDTO> tramos;
}

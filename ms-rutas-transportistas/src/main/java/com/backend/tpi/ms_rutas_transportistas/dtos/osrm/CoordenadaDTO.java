package com.backend.tpi.ms_rutas_transportistas.dtos.osrm;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CoordenadaDTO {
    private Double latitud;
    private Double longitud;
}

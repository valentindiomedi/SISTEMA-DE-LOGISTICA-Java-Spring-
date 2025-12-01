package com.backend.tpi.ms_rutas_transportistas.dtos.osrm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RutaCalculadaDTO {
    private Double distanciaKm;
    private Double duracionHoras;
    private Double duracionMinutos;
    private String geometry;  // polyline para visualización
    private String resumen;
    private boolean exitoso;
    private String mensaje;
}

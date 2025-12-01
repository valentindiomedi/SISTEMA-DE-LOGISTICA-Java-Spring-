package com.backend.tpi.ms_rutas_transportistas.dtos.osrm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OSRMLeg {
    private Double distance;  // en metros
    private Double duration;  // en segundos
    private String summary;
    private List<OSRMStep> steps;
}

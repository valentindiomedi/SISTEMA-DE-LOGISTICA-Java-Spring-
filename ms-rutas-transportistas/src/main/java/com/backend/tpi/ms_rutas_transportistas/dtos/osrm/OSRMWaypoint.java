package com.backend.tpi.ms_rutas_transportistas.dtos.osrm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OSRMWaypoint {
    private String name;
    private List<Double> location;  // [lon, lat]
    private Double distance;  // distancia al punto más cercano en la red
}

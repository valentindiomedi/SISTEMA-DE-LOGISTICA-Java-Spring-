package com.backend.tpi.ms_rutas_transportistas.dtos.osrm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OSRMRouteResponse {
    private String code;
    private List<OSRMRoute> routes;
    private List<OSRMWaypoint> waypoints;
}

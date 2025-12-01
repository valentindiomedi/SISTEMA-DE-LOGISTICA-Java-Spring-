package com.backend.tpi.ms_gestion_calculos.dtos;

import lombok.Data;
import java.util.List;

@Data
public class TarifaDTO {
    private Long id;
    private String nombre;
    private Double costoBaseGestionFijo;
    private Double valorLitroCombustible;
    private List<TarifaVolumenPesoDTO> rangos;
}

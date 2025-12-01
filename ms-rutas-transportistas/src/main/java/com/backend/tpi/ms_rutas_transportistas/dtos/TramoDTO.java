package com.backend.tpi.ms_rutas_transportistas.dtos;

import lombok.Data;

@Data
public class TramoDTO {
    private Long id;
    private Long idRuta;
    private Integer orden;
    private Boolean generadoAutomaticamente;
    private Double duracionHoras;
    private Long origenDepositoId;
    private Long destinoDepositoId;
    private Double distancia;
    private String camionDominio;
    private java.math.BigDecimal origenLat;
    private java.math.BigDecimal origenLong;
    private java.math.BigDecimal destinoLat;
    private java.math.BigDecimal destinoLong;
    private java.math.BigDecimal costoAproximado;
    private java.math.BigDecimal costoReal;
    private java.time.LocalDateTime fechaHoraInicioEstimada;
    private java.time.LocalDateTime fechaHoraFinEstimada;
    private java.time.LocalDateTime fechaHoraInicioReal;
    private java.time.LocalDateTime fechaHoraFinReal;
}

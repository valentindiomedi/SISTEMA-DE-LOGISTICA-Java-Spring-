package com.backend.tpi.ms_rutas_transportistas.models;

import jakarta.persistence.*;
import lombok.Data;

/**
 * Tramo de una ruta
 * Representa un segmento del recorrido (ej: depósito A -> depósito B)
 * Incluye información de camión asignado, costos y tiempos
 */
@Entity
@Table(name = "tramos")
@Data
public class Tramo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tramo")
    private Long id;
    
    // Orden del tramo en la ruta (1, 2, 3, etc.)
    private Integer orden;
    
    // Indica si el tramo fue generado automáticamente por cálculo de ruta
    private Boolean generadoAutomaticamente;
    
    // Duración estimada del tramo en horas (calculada por OSRM)
    private Double duracionHoras;
    
    // According to DER: origin/destination reference deposits and lat/long coords
    private Long origenDepositoId;
    private Long destinoDepositoId;
    private java.math.BigDecimal origenLat;
    private java.math.BigDecimal origenLong;
    private java.math.BigDecimal destinoLat;
    private java.math.BigDecimal destinoLong;
    private Double distancia;

    @ManyToOne
    @JoinColumn(name = "ruta_id")
    private Ruta ruta;

    @ManyToOne
    @JoinColumn(name = "tipo_tramo_id")
    private TipoTramo tipoTramo;

    @ManyToOne
    @JoinColumn(name = "estado_tramo_id")
    private EstadoTramo estado;
    
    // Camion dominio (patente) referenciado in DER
    private String camionDominio;
    private java.math.BigDecimal costoAproximado;
    private java.math.BigDecimal costoReal;
    private java.time.LocalDateTime fechaHoraInicioEstimada;
    private java.time.LocalDateTime fechaHoraFinEstimada;
    private java.time.LocalDateTime fechaHoraInicioReal;
    private java.time.LocalDateTime fechaHoraFinReal;
}

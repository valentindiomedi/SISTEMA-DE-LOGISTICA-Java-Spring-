package com.backend.tpi.ms_solicitudes.models;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Solicitud de transporte
 * Representa la solicitud de un cliente para transportar un contenedor
 * desde un origen hasta un destino
 */
@Entity
@Data
@Table(name = "solicitudes")
public class Solicitud {
    @Id
    @GeneratedValue(generator = "lowest-available-id")
    @org.hibernate.annotations.GenericGenerator(
        name = "lowest-available-id",
        strategy = "com.backend.tpi.ms_solicitudes.config.LowestAvailableIdGenerator"
    )
    @Column(name = "id_solicitud")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "contenedor_id")
    private Contenedor contenedor;

    @Column(name = "cliente_id")
    private Long clienteId;
    
    @CreationTimestamp
    @Column(name = "fecha_creacion", updatable = false)
    private LocalDateTime fechaCreacion;
    
    @UpdateTimestamp
    @Column(name = "fecha_modificacion")
    private LocalDateTime fechaModificacion;

    @Column(name = "origen_lat")
    private BigDecimal origenLat;

    @Column(name = "origen_long")
    private BigDecimal origenLong;

    @Column(name = "destino_lat")
    private BigDecimal destinoLat;

    @Column(name = "destino_long")
    private BigDecimal destinoLong;

    @Column(name = "direccion_origen")
    private String direccionOrigen;

    @Column(name = "direccion_destino")
    private String direccionDestino;

    @ManyToOne
    @JoinColumn(name = "estado_solicitud_id")
    private EstadoSolicitud estado;

    @Column(name = "costo_estimado")
    private BigDecimal costoEstimado;

    @Column(name = "costo_final")
    private BigDecimal costoFinal;

    @Column(name = "tiempo_estimado")
    private BigDecimal tiempoEstimado;

    @Column(name = "tiempo_real")
    private BigDecimal tiempoReal;

    @Column(name = "ruta_id")
    private Long rutaId;

    @Column(name = "tarifa_id")
    private Long tarifaId;
}

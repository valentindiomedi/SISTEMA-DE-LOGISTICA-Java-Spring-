package com.backend.tpi.ms_solicitudes.models;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Contenedor de carga
 * Representa un contenedor con peso y volumen que debe ser transportado
 */
@Entity
@Data
@Table(name = "contenedores")
public class Contenedor {
    @Id
    @GeneratedValue(generator = "lowest-available-id")
    @org.hibernate.annotations.GenericGenerator(
        name = "lowest-available-id",
        strategy = "com.backend.tpi.ms_solicitudes.config.LowestAvailableIdGenerator"
    )
    @Column(name = "id_contenedor")
    private Long id;

    private BigDecimal peso;

    private BigDecimal volumen;

    @ManyToOne
    @JoinColumn(name = "estado_id")
    private EstadoContenedor estado;

    @Column(name = "cliente_id")
    private Long clienteId;
}

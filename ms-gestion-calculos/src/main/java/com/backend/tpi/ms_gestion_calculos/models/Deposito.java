package com.backend.tpi.ms_gestion_calculos.models;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Depósito de almacenamiento
 * Representa un lugar físico donde se almacenan contenedores
 * Incluye ubicación geográfica y costos de estadía
 */
@Entity
@Data
@Table(name = "depositos")
public class Deposito {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_deposito")
    private Long id;

    private String nombre;

    private String direccion;

    private BigDecimal latitud;

    private BigDecimal longitud;

    @ManyToOne
    @JoinColumn(name = "id_ciudad")
    private Ciudad ciudad;

    @Column(name = "costo_estadia_diario")
    private BigDecimal costoEstadiaDiario;
}

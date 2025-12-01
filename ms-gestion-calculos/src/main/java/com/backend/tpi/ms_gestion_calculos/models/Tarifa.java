package com.backend.tpi.ms_gestion_calculos.models;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Tarifa de transporte
 * Define los costos base de gestión y valor del combustible
 * Se relaciona con TarifaVolumenPeso para cargos adicionales
 */
@Entity
@Data
@Table(name = "tarifas")
public class Tarifa {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tarifa")
    private Long id;

    @Column(name = "costo_base_gestion_fijo")
    private BigDecimal costoBaseGestionFijo;

    @Column(name = "valor_litro_combustible")
    private BigDecimal valorLitroCombustible;

    @OneToMany(mappedBy = "tarifa", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TarifaVolumenPeso> rangos = new ArrayList<>();
}

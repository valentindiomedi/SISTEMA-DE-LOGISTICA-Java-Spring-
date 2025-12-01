package com.backend.tpi.ms_gestion_calculos.models;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "tarifa_volumen_peso")
public class TarifaVolumenPeso {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "id_tarifa")
    private Tarifa tarifa;
    
    @Column(name = "volumen_min")
    private Double volumenMin;
    
    @Column(name = "volumen_max")
    private Double volumenMax;
    
    @Column(name = "peso_min")
    private Double pesoMin;
    
    @Column(name = "peso_max")
    private Double pesoMax;
    
    @Column(name = "costo_por_km_base")
    private Double costoPorKmBase;
}

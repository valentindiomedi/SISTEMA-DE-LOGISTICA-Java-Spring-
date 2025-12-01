package com.backend.tpi.ms_rutas_transportistas.models;

import jakarta.persistence.*;
import lombok.Data;

/**
 * Camión de transporte
 * Representa un vehículo disponible para realizar tramos de rutas
 * Incluye datos de capacidad, costos y transportista asignado
 */
@Entity
@Table(name = "camiones")
@Data
public class Camion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "dominio", nullable = false, unique = true)
    private String dominio; // Patente/dominio del camión
    private String marca;
    private String modelo;
    
    @Column(name = "capacidad_peso_max")
    private Double capacidadPesoMax;
    
    @Column(name = "capacidad_volumen_max")
    private Double capacidadVolumenMax;
    
    @Column(name = "nombre_transportista")
    private String nombreTransportista;
    
    @Column(name = "costo_base")
    private Double costoBase;
    
    @Column(name = "costo_por_km")
    private Double costoPorKm;
    
    @Column(name = "consumo_combustible_promedio")
    private Double consumoCombustiblePromedio; // Litros por km
    
    @Column(name = "numero_transportistas")
    private Integer numeroTransportistas;
    
    private Boolean disponible; // Estado operativo del camión
    private Boolean activo; // Si el camión está activo en el sistema
}

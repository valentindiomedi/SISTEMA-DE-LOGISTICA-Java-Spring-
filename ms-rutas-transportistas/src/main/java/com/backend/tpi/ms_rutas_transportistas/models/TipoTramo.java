package com.backend.tpi.ms_rutas_transportistas.models;

import jakarta.persistence.*;
import lombok.Data;

/**
 * Tipo de tramo
 * Ej: "Nacional", "Internacional", "Urbano", etc.
 */
@Entity
@Data
@Table(name = "tipo_tramo")
public class TipoTramo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nombre;
}

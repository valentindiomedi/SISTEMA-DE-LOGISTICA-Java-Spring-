package com.backend.tpi.ms_solicitudes.models;

import jakarta.persistence.*;
import lombok.Data;

/**
 * Estado de una solicitud
 * Ej: "Pendiente", "En Proceso", "Completada", etc.
 */
@Entity
@Data
@Table(name = "estado_solicitud")
public class EstadoSolicitud {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;
}

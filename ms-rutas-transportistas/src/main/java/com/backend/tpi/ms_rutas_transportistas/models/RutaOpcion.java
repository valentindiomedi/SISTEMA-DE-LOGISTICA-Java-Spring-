package com.backend.tpi.ms_rutas_transportistas.models;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
 

import java.time.LocalDateTime;

@Entity
@Table(name = "ruta_opciones")
@Data
public class RutaOpcion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_ruta_opcion")
    private Long id;

    @Column(name = "ruta_id")
    private Long rutaId;

    @Column(name = "solicitud_id")
    private Long solicitudId;

    

    @Column(name = "opcion_index")
    private Integer opcionIndex;

    @Column(name = "distancia_total")
    private Double distanciaTotal;

    @Column(name = "duracion_total_horas")
    private Double duracionTotalHoras;

    @Column(name = "costo_total")
    private Double costoTotal;

    @Column(name = "depositos_ids", columnDefinition = "text")
    private String depositosIdsJson;

    @Column(name = "depositos_nombres", columnDefinition = "text")
    private String depositosNombresJson;

    @Column(name = "tramos_json", columnDefinition = "text")
    private String tramosJson;

    @Column(name = "geometry", columnDefinition = "text")
    private String geometry;

    @CreationTimestamp
    private LocalDateTime fechaCreacion;
}

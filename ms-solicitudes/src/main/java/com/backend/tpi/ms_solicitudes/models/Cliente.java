package com.backend.tpi.ms_solicitudes.models;

import jakarta.persistence.*;
import lombok.Data;

/**
 * Cliente del sistema
 * Representa a una persona o empresa que solicita servicios de transporte
 */
@Entity
@Data
@Table(name = "clientes")
public class Cliente {
    @Id
    @GeneratedValue(generator = "lowest-available-id")
    @org.hibernate.annotations.GenericGenerator(
        name = "lowest-available-id",
        strategy = "com.backend.tpi.ms_solicitudes.config.LowestAvailableIdGenerator"
    )
    @Column(name = "id_cliente")
    private Long id;

    private String nombre;

    private String email;

    private String telefono;
    
    @Column(name = "keycloak_user_id")
    private String keycloakUserId;
}

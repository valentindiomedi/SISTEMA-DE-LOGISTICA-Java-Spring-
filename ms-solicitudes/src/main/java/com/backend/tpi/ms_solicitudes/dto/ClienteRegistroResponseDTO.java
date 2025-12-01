package com.backend.tpi.ms_solicitudes.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de respuesta para el registro de clientes
 * Contiene información del cliente creado sin exponer datos sensibles
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ClienteRegistroResponseDTO {
    
    private Long id;
    private String nombre;
    private String email;
    private String telefono;
    private String username;
    private String keycloakUserId;
    private String mensaje;
}

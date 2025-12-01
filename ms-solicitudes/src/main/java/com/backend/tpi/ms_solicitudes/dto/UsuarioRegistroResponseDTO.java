package com.backend.tpi.ms_solicitudes.dto;

import lombok.Data;

/**
 * DTO de respuesta para el registro de usuarios
 */
@Data
public class UsuarioRegistroResponseDTO {
    private String keycloakUserId;
    private String nombre;
    private String email;
    private String telefono;
    private String username;
    private String rol;
    private String mensaje;
}

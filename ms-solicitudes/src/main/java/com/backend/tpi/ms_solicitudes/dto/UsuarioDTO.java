package com.backend.tpi.ms_solicitudes.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO para representar un usuario del sistema
 * Usado en respuestas de consultas de usuarios
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioDTO {
    
    private String id;
    private String username;
    private String email;
    private String nombre;
    private String apellido;
    private Boolean enabled;
    private Boolean emailVerified;
    private List<String> roles;
}

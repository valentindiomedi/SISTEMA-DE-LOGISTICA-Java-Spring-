package com.backend.tpi.ms_solicitudes.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO para el registro de nuevos usuarios por parte del administrador
 * Permite crear usuarios con diferentes roles en el sistema
 */
@Data
public class UsuarioRegistroDTO {
    
    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 2, max = 100, message = "El nombre debe tener entre 2 y 100 caracteres")
    private String nombre;
    
    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El email debe ser válido")
    private String email;
    
    @Size(max = 20, message = "El teléfono debe tener máximo 20 caracteres")
    private String telefono;
    
    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 4, message = "La contraseña debe tener al menos 4 caracteres")
    private String password;
    
    @NotBlank(message = "El username es obligatorio")
    @Size(min = 3, max = 50, message = "El username debe tener entre 3 y 50 caracteres")
    private String username;
    
    @NotBlank(message = "El rol es obligatorio")
    @Pattern(regexp = "CLIENTE|OPERADOR|TRANSPORTISTA|ADMIN", 
             message = "El rol debe ser uno de: CLIENTE, OPERADOR, TRANSPORTISTA, ADMIN")
    private String rol;
}

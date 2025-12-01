package com.backend.tpi.ms_solicitudes.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class CreateSolicitudDTO {
    @NotBlank(message = "direccionOrigen es obligatoria")
    private String direccionOrigen;

    @NotBlank(message = "direccionDestino es obligatoria")
    private String direccionDestino;

    private Long contenedorId;

    // Cliente info: nombre, email y telefono son requeridos para este flujo público
    @NotBlank(message = "clienteEmail es obligatorio")
    @Email(message = "clienteEmail debe ser un email válido")
    private String clienteEmail;

    @NotBlank(message = "clienteNombre es obligatorio")
    private String clienteNombre;

    @NotBlank(message = "clienteTelefono es obligatorio")
    private String clienteTelefono;

    // No se requieren credenciales; registro en Keycloak está deshabilitado en este flujo.

    // Datos para crear contenedor inline (si no se pasa contenedorId)
    @Positive(message = "contenedorPeso debe ser mayor a 0")
    private java.math.BigDecimal contenedorPeso;

    @Positive(message = "contenedorVolumen debe ser mayor a 0")
    private java.math.BigDecimal contenedorVolumen;
}

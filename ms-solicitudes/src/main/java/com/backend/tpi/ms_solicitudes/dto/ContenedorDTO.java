package com.backend.tpi.ms_solicitudes.dto;

import lombok.Data;
import java.math.BigDecimal;

/**
 * DTO para recibir datos de contenedor desde el cliente
 * Permite enviar el estado como String (nombre) en lugar del objeto completo
 */
@Data
public class ContenedorDTO {
    private Long id;
    private BigDecimal peso;
    private BigDecimal volumen;
    private Long clienteId;
    private String estado; // Nombre del estado como String (ej: "LIBRE", "ASIGNADO")
}

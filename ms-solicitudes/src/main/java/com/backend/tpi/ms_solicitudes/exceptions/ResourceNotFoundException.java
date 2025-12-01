package com.backend.tpi.ms_solicitudes.exceptions;

/**
 * Excepción personalizada para recursos no encontrados
 * Se lanza cuando se intenta acceder a un recurso que no existe en la base de datos
 */
public class ResourceNotFoundException extends RuntimeException {
    
    /**
     * Constructor con mensaje personalizado
     * @param message Mensaje descriptivo del error
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }
    
    /**
     * Constructor con mensaje y causa
     * @param message Mensaje descriptivo del error
     * @param cause Causa raíz de la excepción
     */
    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * Constructor con tipo de recurso e ID
     * @param resourceType Tipo de recurso no encontrado (ej: "Cliente", "Solicitud")
     * @param resourceId ID del recurso no encontrado
     */
    public ResourceNotFoundException(String resourceType, Object resourceId) {
        super(String.format("%s no encontrado con ID: %s", resourceType, resourceId));
    }
    
    /**
     * Constructor con tipo de recurso, campo e ID
     * @param resourceType Tipo de recurso no encontrado
     * @param fieldName Nombre del campo por el que se busca
     * @param fieldValue Valor del campo que no se encontró
     */
    public ResourceNotFoundException(String resourceType, String fieldName, Object fieldValue) {
        super(String.format("%s no encontrado con %s: %s", resourceType, fieldName, fieldValue));
    }
}

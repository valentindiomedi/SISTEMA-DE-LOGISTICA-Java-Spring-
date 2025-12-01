package com.backend.tpi.ms_solicitudes.services;

import com.backend.tpi.ms_solicitudes.models.EstadoContenedor;
import com.backend.tpi.ms_solicitudes.models.EstadoSolicitud;
import com.backend.tpi.ms_solicitudes.repositories.EstadoContenedorRepository;
import com.backend.tpi.ms_solicitudes.repositories.EstadoSolicitudRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Servicio para gestionar transiciones válidas de estados de Solicitudes y Contenedores
 * Implementa validación de transiciones para evitar cambios inválidos de estado
 */
@Service
@Slf4j
public class EstadoTransicionService {

    @Autowired
    private EstadoSolicitudRepository estadoSolicitudRepository;

    @Autowired
    private EstadoContenedorRepository estadoContenedorRepository;

    // Mapa de transiciones válidas para Solicitudes
    // Key: estado origen, Value: lista de estados destino permitidos
    private static final Map<String, List<String>> TRANSICIONES_SOLICITUD = new HashMap<>();
    
    // Mapa de transiciones válidas para Contenedores
    private static final Map<String, List<String>> TRANSICIONES_CONTENEDOR = new HashMap<>();

    static {
        // Configurar transiciones válidas de Solicitud
        // Estados oficiales: PENDIENTE, PROGRAMADA, EN_TRANSITO, COMPLETADA, CANCELADA
        TRANSICIONES_SOLICITUD.put("PENDIENTE", Arrays.asList("PROGRAMADA", "CANCELADA"));
        TRANSICIONES_SOLICITUD.put("PROGRAMADA", Arrays.asList("EN_TRANSITO", "CANCELADA"));
        TRANSICIONES_SOLICITUD.put("EN_TRANSITO", Arrays.asList("COMPLETADA", "PROGRAMADA"));
        TRANSICIONES_SOLICITUD.put("COMPLETADA", Arrays.asList()); // Estado final
        TRANSICIONES_SOLICITUD.put("CANCELADA", Arrays.asList()); // Estado final

        // Configurar transiciones válidas de Contenedor
        // Estados oficiales: LIBRE, OCUPADO, EN_TRANSITO, EN_DEPOSITO, ENTREGADO
        TRANSICIONES_CONTENEDOR.put("LIBRE", Arrays.asList("OCUPADO"));
        TRANSICIONES_CONTENEDOR.put("OCUPADO", Arrays.asList("EN_TRANSITO", "LIBRE"));
        TRANSICIONES_CONTENEDOR.put("EN_TRANSITO", Arrays.asList("EN_DEPOSITO", "ENTREGADO"));
        TRANSICIONES_CONTENEDOR.put("EN_DEPOSITO", Arrays.asList("EN_TRANSITO"));
        TRANSICIONES_CONTENEDOR.put("ENTREGADO", Arrays.asList("LIBRE"));
    }

    /**
     * Valida si una transición de estado de solicitud es válida
     * @param estadoOrigenNombre Nombre del estado origen (actual)
     * @param estadoDestinoNombre Nombre del estado destino (nuevo)
     * @return true si la transición es válida, false en caso contrario
     */
    public boolean esTransicionSolicitudValida(String estadoOrigenNombre, String estadoDestinoNombre) {
        if (estadoOrigenNombre == null || estadoDestinoNombre == null) {
            log.warn("Estados null en validación de transición");
            return false;
        }

        // Si es el mismo estado, permitir (no hay cambio)
        if (estadoOrigenNombre.equalsIgnoreCase(estadoDestinoNombre)) {
            return true;
        }

        String origenKey = estadoOrigenNombre.toUpperCase().replace(" ", "_");
        String destinoKey = estadoDestinoNombre.toUpperCase().replace(" ", "_");

        List<String> destinosPermitidos = TRANSICIONES_SOLICITUD.get(origenKey);
        
        if (destinosPermitidos == null) {
            log.warn("Estado origen desconocido para solicitud: {}", origenKey);
            return false;
        }

        boolean valido = destinosPermitidos.contains(destinoKey);
        
        if (!valido) {
            log.warn("Transición de solicitud no válida: {} -> {}", origenKey, destinoKey);
        }
        
        return valido;
    }

    /**
     * Valida si una transición de estado de contenedor es válida
     * @param estadoOrigenNombre Nombre del estado origen (actual)
     * @param estadoDestinoNombre Nombre del estado destino (nuevo)
     * @return true si la transición es válida, false en caso contrario
     */
    public boolean esTransicionContenedorValida(String estadoOrigenNombre, String estadoDestinoNombre) {
        if (estadoOrigenNombre == null || estadoDestinoNombre == null) {
            log.warn("Estados null en validación de transición");
            return false;
        }

        // Si es el mismo estado, permitir (no hay cambio)
        if (estadoOrigenNombre.equalsIgnoreCase(estadoDestinoNombre)) {
            return true;
        }

        String origenKey = estadoOrigenNombre.toUpperCase().replace(" ", "_");
        String destinoKey = estadoDestinoNombre.toUpperCase().replace(" ", "_");

        List<String> destinosPermitidos = TRANSICIONES_CONTENEDOR.get(origenKey);
        
        if (destinosPermitidos == null) {
            log.warn("Estado origen desconocido para contenedor: {}", origenKey);
            return false;
        }

        boolean valido = destinosPermitidos.contains(destinoKey);
        
        if (!valido) {
            log.warn("Transición de contenedor no válida: {} -> {}", origenKey, destinoKey);
        }
        
        return valido;
    }

    /**
     * Obtiene los estados permitidos desde un estado de solicitud dado
     * @param estadoOrigenNombre Nombre del estado origen
     * @return Lista de nombres de estados permitidos
     */
    public List<String> getEstadosPermitidosSolicitud(String estadoOrigenNombre) {
        if (estadoOrigenNombre == null) {
            return Collections.emptyList();
        }
        
        String origenKey = estadoOrigenNombre.toUpperCase().replace(" ", "_");
        List<String> permitidos = TRANSICIONES_SOLICITUD.get(origenKey);
        
        return permitidos != null ? new ArrayList<>(permitidos) : Collections.emptyList();
    }

    /**
     * Obtiene los estados permitidos desde un estado de contenedor dado
     * @param estadoOrigenNombre Nombre del estado origen
     * @return Lista de nombres de estados permitidos
     */
    public List<String> getEstadosPermitidosContenedor(String estadoOrigenNombre) {
        if (estadoOrigenNombre == null) {
            return Collections.emptyList();
        }
        
        String origenKey = estadoOrigenNombre.toUpperCase().replace(" ", "_");
        List<String> permitidos = TRANSICIONES_CONTENEDOR.get(origenKey);
        
        return permitidos != null ? new ArrayList<>(permitidos) : Collections.emptyList();
    }

    /**
     * Busca un estado de solicitud por nombre
     * @param nombre Nombre del estado
     * @return Estado encontrado
     * @throws IllegalArgumentException si no existe el estado
     */
    public EstadoSolicitud getEstadoSolicitudByNombre(String nombre) {
        return estadoSolicitudRepository.findByNombre(nombre)
                .orElseThrow(() -> {
                    log.error("Estado de solicitud no encontrado: {}", nombre);
                    return new IllegalArgumentException("Estado de solicitud no encontrado: " + nombre);
                });
    }

    /**
     * Busca un estado de contenedor por nombre
     * @param nombre Nombre del estado
     * @return Estado encontrado
     * @throws IllegalArgumentException si no existe el estado
     */
    public EstadoContenedor getEstadoContenedorByNombre(String nombre) {
        return estadoContenedorRepository.findByNombre(nombre)
                .orElseThrow(() -> {
                    log.error("Estado de contenedor no encontrado: {}", nombre);
                    return new IllegalArgumentException("Estado de contenedor no encontrado: " + nombre);
                });
    }
}

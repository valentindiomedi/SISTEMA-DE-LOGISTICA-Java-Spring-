package com.backend.tpi.ms_solicitudes.services;

import com.backend.tpi.ms_solicitudes.dtos.SeguimientoContenedorDTO;
import com.backend.tpi.ms_solicitudes.models.Contenedor;
import com.backend.tpi.ms_solicitudes.models.EstadoContenedor;
import com.backend.tpi.ms_solicitudes.models.Solicitud;
import com.backend.tpi.ms_solicitudes.repositories.ContenedorRepository;
import com.backend.tpi.ms_solicitudes.repositories.SolicitudRepository;
import com.backend.tpi.ms_solicitudes.repositories.EstadoContenedorRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Servicio de negocio para Contenedores
 * Gestiona operaciones CRUD de contenedores y su seguimiento de ubicación
 */
@Service
@Slf4j
public class ContenedorService {

    @Autowired
    private ContenedorRepository contenedorRepository;

    @Autowired
    private SolicitudRepository solicitudRepository;
    
    @Autowired
    private EstadoContenedorRepository estadoContenedorRepository;
    
    @Autowired
    private EstadoTransicionService estadoTransicionService;
    
    @Autowired
    private com.backend.tpi.ms_solicitudes.repositories.EstadoSolicitudRepository estadoSolicitudRepository;

    /**
     * Obtiene todos los contenedores del sistema
     * @return Lista con todos los contenedores
     */
    @Transactional(readOnly = true)
    public List<Contenedor> findAll() {
        return contenedorRepository.findAll();
    }
    
    /**
     * Busca contenedores por estado
     * @param estadoId ID del estado a filtrar
     * @return Lista de contenedores con ese estado
     */
    public List<Contenedor> findByEstado(Long estadoId) {
        EstadoContenedor estado = estadoContenedorRepository.findById(estadoId)
            .orElseThrow(() -> new RuntimeException("Estado no encontrado con ID: " + estadoId));
        return contenedorRepository.findByEstado(estado);
    }
    
    /**
     * Busca contenedores por nombre de estado
     * @param estadoNombre Nombre del estado a filtrar
     * @return Lista de contenedores con ese estado
     */
    public List<Contenedor> findByEstadoNombre(String estadoNombre) {
        EstadoContenedor estado = estadoContenedorRepository.findByNombre(estadoNombre)
            .orElseThrow(() -> new RuntimeException("Estado no encontrado: " + estadoNombre));
        return contenedorRepository.findByEstado(estado);
    }

    /**
     * Busca un contenedor por su ID
     * @param id ID del contenedor
     * @return Contenedor encontrado
     * @throws RuntimeException si no se encuentra el contenedor
     */
    @Transactional(readOnly = true)
    public Contenedor findById(Long id) {
        return contenedorRepository.findById(id)
                .orElseThrow(() -> new com.backend.tpi.ms_solicitudes.exceptions.ResourceNotFoundException("Contenedor", id));
    }

    /**
     * Busca todos los contenedores de un cliente específico
     * @param clienteId ID del cliente
     * @return Lista de contenedores del cliente
     */
    @Transactional(readOnly = true)
    public List<Contenedor> findByClienteId(Long clienteId) {
        return contenedorRepository.findAll().stream()
                .filter(c -> clienteId.equals(c.getClienteId()))
                .toList();
    }

    /**
     * Guarda un nuevo contenedor en la base de datos
     * @param contenedor Contenedor a guardar
     * @return Contenedor guardado con su ID asignado
     * @throws IllegalArgumentException si los datos del contenedor son inválidos
     */
    @Transactional
    public Contenedor save(Contenedor contenedor) {
        // Validar datos de entrada
        if (contenedor == null) {
            log.error("Contenedor no puede ser null");
            throw new IllegalArgumentException("Los datos del contenedor no pueden ser null");
        }
        if (contenedor.getClienteId() == null) {
            log.error("ClienteId no puede ser null");
            throw new IllegalArgumentException("El ID del cliente es obligatorio");
        }
        if (contenedor.getPeso() != null && contenedor.getPeso().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            log.error("Peso inválido: {}", contenedor.getPeso());
            throw new IllegalArgumentException("El peso debe ser mayor a 0");
        }
        if (contenedor.getVolumen() != null && contenedor.getVolumen().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            log.error("Volumen inválido: {}", contenedor.getVolumen());
            throw new IllegalArgumentException("El volumen debe ser mayor a 0");
        }
        
        log.info("Guardando contenedor para cliente ID: {}", contenedor.getClienteId());
        // Asignar estado por defecto LIBRE si no se proporcionó
        try {
            if (contenedor.getEstado() == null && estadoContenedorRepository != null) {
                estadoContenedorRepository.findByNombre("LIBRE").ifPresent(contenedor::setEstado);
            }
        } catch (Exception e) {
            log.warn("No se pudo asignar estado por defecto al contenedor: {}", e.getMessage());
        }
        return contenedorRepository.save(contenedor);
    }

    /**
     * Actualiza los datos de un contenedor existente
     * @param id ID del contenedor a actualizar
     * @param contenedorActualizado Datos actualizados del contenedor
     * @return Contenedor actualizado
     */
    @Transactional
    public Contenedor update(Long id, Contenedor contenedorActualizado) {
        Contenedor contenedor = findById(id);
        contenedor.setPeso(contenedorActualizado.getPeso());
        contenedor.setVolumen(contenedorActualizado.getVolumen());
        contenedor.setEstado(contenedorActualizado.getEstado());
        contenedor.setClienteId(contenedorActualizado.getClienteId());
        log.info("Actualizando contenedor ID: {}", id);
        return contenedorRepository.save(contenedor);
    }

    /**
     * Elimina un contenedor por su ID
     * @param id ID del contenedor a eliminar
     * @throws RuntimeException si el contenedor tiene solicitudes asignadas
     */
    @Transactional
    public void deleteById(Long id) {
        Contenedor contenedor = findById(id);
        
        // Validar que no tenga solicitudes asignadas
        long cantidadSolicitudes = solicitudRepository.countByContenedor_Id(id);
        if (cantidadSolicitudes > 0) {
            throw new RuntimeException("No se puede eliminar el contenedor ID " + id + 
                " porque tiene " + cantidadSolicitudes + " solicitud(es) asignada(s). " +
                "Debe eliminar primero las solicitudes asociadas.");
        }
        
        log.info("Eliminando contenedor ID: {}", id);
        contenedorRepository.delete(contenedor);
    }

    /**
     * Actualiza únicamente el estado de un contenedor con validación de transición
     * @param id ID del contenedor
     * @param estadoId ID del nuevo estado
     * @return Contenedor con estado actualizado
     * @throws IllegalStateException si la transición no es válida
     */
    @Transactional
    public Contenedor updateEstado(Long id, Long estadoId) {
        log.info("Actualizando estado del contenedor ID: {} a estado ID: {}", id, estadoId);
        Contenedor contenedor = findById(id);
        
        // Obtener el estado destino
        EstadoContenedor estadoDestino = estadoContenedorRepository.findById(estadoId)
            .orElseThrow(() -> {
                log.error("Estado de contenedor no encontrado con ID: {}", estadoId);
                return new IllegalArgumentException("Estado de contenedor no encontrado con ID: " + estadoId);
            });
        
        // Validar transición si hay estado actual
        if (contenedor.getEstado() != null) {
            String estadoOrigenNombre = contenedor.getEstado().getNombre();
            String estadoDestinoNombre = estadoDestino.getNombre();
            
            if (!estadoTransicionService.esTransicionContenedorValida(estadoOrigenNombre, estadoDestinoNombre)) {
                log.error("Transición de estado inválida de {} a {}", estadoOrigenNombre, estadoDestinoNombre);
                throw new IllegalStateException(
                    String.format("No se puede cambiar el estado de '%s' a '%s'. Transición no permitida.", 
                        estadoOrigenNombre, estadoDestinoNombre)
                );
            }
            log.debug("Transición válida de {} a {}", estadoOrigenNombre, estadoDestinoNombre);
        }
        
        contenedor.setEstado(estadoDestino);
        log.info("Estado del contenedor ID: {} actualizado exitosamente a {}", id, estadoDestino.getNombre());
        Contenedor contenedorActualizado = contenedorRepository.save(contenedor);
        
        // Si el contenedor cambió a ENTREGADO, cambiar la solicitud activa a COMPLETADA
        if ("ENTREGADO".equals(estadoDestino.getNombre())) {
            try {
                // Buscar la solicitud activa del contenedor
                Optional<Solicitud> solicitudOpt = solicitudRepository.findByContenedor_Id(contenedor.getId())
                    .stream()
                    .filter(s -> s.getEstado() != null && "EN_TRANSITO".equals(s.getEstado().getNombre()))
                    .findFirst();
                
                if (solicitudOpt.isPresent()) {
                    Solicitud solicitud = solicitudOpt.get();
                    
                    // Buscar el estado COMPLETADA
                    Optional<com.backend.tpi.ms_solicitudes.models.EstadoSolicitud> estadoCompletadaOpt = 
                        estadoSolicitudRepository.findByNombre("COMPLETADA");
                    
                    if (estadoCompletadaOpt.isPresent()) {
                        solicitud.setEstado(estadoCompletadaOpt.get());
                        solicitudRepository.save(solicitud);
                        log.info("Solicitud ID: {} cambiada automáticamente a COMPLETADA por contenedor ENTREGADO", solicitud.getId());
                    } else {
                        log.warn("No se encontró el estado COMPLETADA para actualizar la solicitud");
                    }
                } else {
                    log.debug("No se encontró solicitud EN_TRANSITO para el contenedor ID: {}", contenedor.getId());
                }
            } catch (Exception e) {
                log.error("Error al actualizar estado de solicitud cuando contenedor cambió a ENTREGADO: {}", e.getMessage(), e);
                // No lanzamos la excepción para no fallar la actualización del contenedor
            }
        }
        
        return contenedorActualizado;
    }

    /**
     * Obtiene información de seguimiento de un contenedor (ubicación, estado, depósito)
     * Determina la ubicación según el estado de la solicitud activa
     * @param id ID del contenedor
     * @return DTO con información de seguimiento del contenedor
     */
    @Transactional(readOnly = true)
    public SeguimientoContenedorDTO getSeguimiento(Long id) {
        Contenedor contenedor = findById(id);
        
        SeguimientoContenedorDTO seguimiento = new SeguimientoContenedorDTO();
        seguimiento.setIdContenedor(contenedor.getId());
        
        if (contenedor.getEstado() != null) {
            seguimiento.setEstadoActual(contenedor.getEstado().getNombre());
        }
        
        // Buscar la solicitud activa más reciente del contenedor
        Optional<Solicitud> solicitudOpt = solicitudRepository.findFirstByContenedor_IdOrderByIdDesc(id);
        
        if (solicitudOpt.isPresent()) {
            Solicitud solicitud = solicitudOpt.get();
            
            // Determinar la ubicación según el estado de la solicitud
            if (solicitud.getEstado() != null) {
                String estadoSolicitud = solicitud.getEstado().getNombre().toLowerCase();
                
                switch (estadoSolicitud) {
                    case "en_transito":
                    case "en_camino":
                    case "en_ruta":
                        // Si está en tránsito, usamos la ubicación de destino
                        // En una implementación real, consultarías la posición actual del camión
                        // desde ms-rutas-transportistas usando el rutaId
                        seguimiento.setUbicacionActualLat(solicitud.getDestinoLat());
                        seguimiento.setUbicacionActualLong(solicitud.getDestinoLong());
                        seguimiento.setDepositoId(null);
                        break;
                        
                    case "entregado":
                    case "finalizado":
                        // Si fue entregado, está en destino
                        seguimiento.setUbicacionActualLat(solicitud.getDestinoLat());
                        seguimiento.setUbicacionActualLong(solicitud.getDestinoLong());
                        seguimiento.setDepositoId(null);
                        break;
                        
                    case "pendiente":
                    case "programada":
                    case "programado":
                    default:
                        // Si está pendiente/programada, asumir que se encuentra en el origen (depósito)
                        seguimiento.setUbicacionActualLat(solicitud.getOrigenLat());
                        seguimiento.setUbicacionActualLong(solicitud.getOrigenLong());
                        // TODO: Obtener depositoId desde ms-gestion-calculos si está almacenado
                        seguimiento.setDepositoId(null);
                        break;
                }
            }
        } else {
            // Si no hay solicitud activa, el contenedor está en un depósito sin solicitud
            seguimiento.setUbicacionActualLat(null);
            seguimiento.setUbicacionActualLong(null);
            seguimiento.setDepositoId(null); // TODO: Consultar tabla de ubicación de contenedores si existe
        }
        
        return seguimiento;
    }

    /**
     * Consulta los estados a los que puede transicionar un contenedor desde su estado actual
     * @param id ID del contenedor
     * @return Lista de nombres de estados permitidos
     */
    @Transactional(readOnly = true)
    public List<String> getEstadosPermitidos(Long id) {
        log.info("Consultando estados permitidos para contenedor ID: {}", id);
        Contenedor contenedor = findById(id);
        
        if (contenedor.getEstado() == null) {
            log.warn("El contenedor ID: {} no tiene estado actual asignado", id);
            return List.of(); // Sin estado actual, no hay transiciones
        }
        
        String estadoActual = contenedor.getEstado().getNombre();
        List<String> permitidos = estadoTransicionService.getEstadosPermitidosContenedor(estadoActual);
        log.info("Contenedor ID: {} en estado '{}' puede transicionar a: {}", id, estadoActual, permitidos);
        return permitidos;
    }
}

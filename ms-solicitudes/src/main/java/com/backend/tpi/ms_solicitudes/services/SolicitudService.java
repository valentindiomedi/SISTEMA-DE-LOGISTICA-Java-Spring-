package com.backend.tpi.ms_solicitudes.services;

import com.backend.tpi.ms_solicitudes.dtos.CreateSolicitudDTO;
import com.backend.tpi.ms_solicitudes.dtos.SolicitudDTO;
import com.backend.tpi.ms_solicitudes.models.Solicitud;
import com.backend.tpi.ms_solicitudes.repositories.SolicitudRepository;
import com.backend.tpi.ms_solicitudes.repositories.EstadoSolicitudRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Servicio de negocio para Solicitudes
 * Gestiona la lógica de solicitudes y se comunica con otros microservicios
 * (ms-gestion-calculos para precios y ms-rutas-transportistas para rutas)
 */
@Service
public class SolicitudService {

    private static final Logger logger = LoggerFactory.getLogger(SolicitudService.class);

    @Autowired
    private SolicitudRepository solicitudRepository;

    @Autowired
    private org.springframework.web.client.RestClient calculosClient;

    @Autowired
    private org.springframework.web.client.RestClient rutasClient;

    @Autowired
    private EstadoTransicionService estadoTransicionService;
    
    @Autowired
    private EstadoSolicitudRepository estadoSolicitudRepository;
    
    @Autowired
    private com.backend.tpi.ms_solicitudes.repositories.EstadoContenedorRepository estadoContenedorRepository;
    
    @Autowired
    private GeocodificacionService geocodificacionService;
    
    @Autowired
    private com.backend.tpi.ms_solicitudes.services.ContenedorService contenedorService;

    @Autowired
    private com.backend.tpi.ms_solicitudes.services.ClienteService clienteService;

    

    // Base URLs for other microservices (provide defaults for local/docker environment)
    @Value("${app.calculos.base-url:http://ms-gestion-calculos:8081}")
    private String calculosBaseUrl;

    @Value("${app.rutas.base-url:http://ms-rutas-transportistas:8082}")
    private String rutasBaseUrl;

    // Manual mapping - removed ModelMapper dependency

    /**
         * Crea una nueva solicitud de transporte
         * @param createSolicitudDTO Datos de la solicitud a crear
         * @return DTO con los datos de la solicitud creada
         * @throws IllegalArgumentException si los datos son inválidos
         */
        public SolicitudDTO create(CreateSolicitudDTO createSolicitudDTO) {
            // Validar datos de entrada
            if (createSolicitudDTO == null) {
                logger.error("CreateSolicitudDTO no puede ser null");
                throw new IllegalArgumentException("Los datos de la solicitud no pueden ser null");
            }
            if (createSolicitudDTO.getDireccionOrigen() == null || createSolicitudDTO.getDireccionOrigen().trim().isEmpty()) {
                logger.error("Dirección de origen no puede ser null o vacía");
                throw new IllegalArgumentException("La dirección de origen es obligatoria");
            }
            if (createSolicitudDTO.getDireccionDestino() == null || createSolicitudDTO.getDireccionDestino().trim().isEmpty()) {
                logger.error("Dirección de destino no puede ser null o vacía");
                throw new IllegalArgumentException("La dirección de destino es obligatoria");
            }
            if (createSolicitudDTO.getDireccionOrigen().equals(createSolicitudDTO.getDireccionDestino())) {
                logger.error("Dirección de origen y destino no pueden ser iguales");
                throw new IllegalArgumentException("La dirección de origen y destino deben ser diferentes");
            }
            
            logger.debug("Creando nueva solicitud - origen: {}, destino: {}", 
                createSolicitudDTO.getDireccionOrigen(), createSolicitudDTO.getDireccionDestino());
            Solicitud solicitud = new Solicitud();
            // Map fields from DTO to entity
            solicitud.setDireccionOrigen(createSolicitudDTO.getDireccionOrigen());
            solicitud.setDireccionDestino(createSolicitudDTO.getDireccionDestino());
            // Validación: no permitir que la dirección sea un ID de depósito (se debe enviar dirección de texto o coordenadas)
            if (geocodificacionService.isDireccionDeposito(createSolicitudDTO.getDireccionOrigen())) {
                logger.error("No se permiten IDs de depósito en la dirección de origen al crear solicitud: {}", createSolicitudDTO.getDireccionOrigen());
                throw new IllegalArgumentException("No pasar IDs de depósito en las solicitudes. Enviar dirección de texto o coordenadas.");
            }
            if (geocodificacionService.isDireccionDeposito(createSolicitudDTO.getDireccionDestino())) {
                logger.error("No se permiten IDs de depósito en la dirección de destino al crear solicitud: {}", createSolicitudDTO.getDireccionDestino());
                throw new IllegalArgumentException("No pasar IDs de depósito en las solicitudes. Enviar dirección de texto o coordenadas.");
            }
            
            // Geocodificar direcciones a coordenadas
            logger.debug("Geocodificando dirección de origen: {}", createSolicitudDTO.getDireccionOrigen());
            com.backend.tpi.ms_solicitudes.dtos.CoordenadaDTO coordOrigen = geocodificacionService.geocodificar(
                createSolicitudDTO.getDireccionOrigen());
            if (coordOrigen != null) {
                solicitud.setOrigenLat(geocodificacionService.toBigDecimal(coordOrigen.getLatitud()));
                solicitud.setOrigenLong(geocodificacionService.toBigDecimal(coordOrigen.getLongitud()));
                logger.info("Coordenadas de origen geocodificadas: lat={}, lon={}", 
                    coordOrigen.getLatitud(), coordOrigen.getLongitud());
            } else {
                logger.warn("No se pudo geocodificar la dirección de origen: {}", createSolicitudDTO.getDireccionOrigen());
            }
            
            logger.debug("Geocodificando dirección de destino: {}", createSolicitudDTO.getDireccionDestino());
            com.backend.tpi.ms_solicitudes.dtos.CoordenadaDTO coordDestino = geocodificacionService.geocodificar(
                createSolicitudDTO.getDireccionDestino());
            if (coordDestino != null) {
                solicitud.setDestinoLat(geocodificacionService.toBigDecimal(coordDestino.getLatitud()));
                solicitud.setDestinoLong(geocodificacionService.toBigDecimal(coordDestino.getLongitud()));
                logger.info("Coordenadas de destino geocodificadas: lat={}, lon={}", 
                    coordDestino.getLatitud(), coordDestino.getLongitud());
            } else {
                logger.warn("No se pudo geocodificar la dirección de destino: {}", createSolicitudDTO.getDireccionDestino());
            }
            
            // Manejo de cliente: si se proveen credenciales intentamos registrar en Keycloak,
            // si no, buscamos por email y si no existe creamos cliente mínimo en BD
            try {
                // Buscar cliente por email; si no existe crear un registro mínimo en BD
                try {
                    if (createSolicitudDTO.getClienteEmail() != null && !createSolicitudDTO.getClienteEmail().isBlank()) {
                        try {
                            com.backend.tpi.ms_solicitudes.models.Cliente found = clienteService.findByEmail(createSolicitudDTO.getClienteEmail());
                            solicitud.setClienteId(found.getId());
                        } catch (Exception ex) {
                            // No existe: crear cliente mínimo (local)
                            com.backend.tpi.ms_solicitudes.models.Cliente nuevo = new com.backend.tpi.ms_solicitudes.models.Cliente();
                            nuevo.setEmail(createSolicitudDTO.getClienteEmail());
                            nuevo.setNombre(createSolicitudDTO.getClienteNombre() != null ? createSolicitudDTO.getClienteNombre() : "Cliente");
                            nuevo.setTelefono(createSolicitudDTO.getClienteTelefono());
                            com.backend.tpi.ms_solicitudes.models.Cliente guardado = clienteService.save(nuevo);
                            solicitud.setClienteId(guardado.getId());
                        }
                    }
                } catch (Exception e) {
                    logger.warn("No se pudo procesar la información del cliente en la creación de solicitud: {}", e.getMessage());
                }
            } catch (Exception e) {
                logger.warn("No se pudo procesar la información del cliente en la creación de solicitud: {}", e.getMessage());
            }

            // Manejo de contenedor: si se pasó contenedorId lo asociamos; si no, y se dio identificacion, creamos inline
            try {
                if (createSolicitudDTO.getContenedorId() != null) {
                    com.backend.tpi.ms_solicitudes.models.Contenedor cont = contenedorService.findById(createSolicitudDTO.getContenedorId());
                    
                    // Validar que el clienteId del contenedor coincida con el de la solicitud
                    if (solicitud.getClienteId() != null && cont.getClienteId() != null) {
                        if (!solicitud.getClienteId().equals(cont.getClienteId())) {
                            logger.error("El contenedor ID {} pertenece al cliente {}, pero la solicitud es del cliente {}",
                                cont.getId(), cont.getClienteId(), solicitud.getClienteId());
                            throw new IllegalArgumentException("El contenedor no pertenece al cliente de la solicitud");
                        }
                    }
                    
                    solicitud.setContenedor(cont);
                    logger.info("Contenedor existente ID {} asociado a la solicitud", cont.getId());
                    
                    // Cambiar estado del contenedor a OCUPADO cuando se asigna a una solicitud
                    try {
                        java.util.Optional<com.backend.tpi.ms_solicitudes.models.EstadoContenedor> estadoOcupado = 
                            estadoContenedorRepository.findByNombre("OCUPADO");
                        if (estadoOcupado.isPresent()) {
                            contenedorService.updateEstado(cont.getId(), estadoOcupado.get().getId());
                            logger.info("Estado del contenedor {} cambiado a OCUPADO", cont.getId());
                        }
                    } catch (Exception ex) {
                        logger.warn("No se pudo cambiar el estado del contenedor a OCUPADO: {}", ex.getMessage());
                    }
                } else if (createSolicitudDTO.getContenedorPeso() != null || createSolicitudDTO.getContenedorVolumen() != null) {
                    com.backend.tpi.ms_solicitudes.models.Contenedor nuevoCont = new com.backend.tpi.ms_solicitudes.models.Contenedor();
                    nuevoCont.setPeso(createSolicitudDTO.getContenedorPeso());
                    nuevoCont.setVolumen(createSolicitudDTO.getContenedorVolumen());
                    // asociado al cliente si fue creado o provisto
                    if (solicitud.getClienteId() != null) nuevoCont.setClienteId(solicitud.getClienteId());
                    
                    // Asignar estado OCUPADO al nuevo contenedor
                    try {
                        java.util.Optional<com.backend.tpi.ms_solicitudes.models.EstadoContenedor> estadoOcupado = 
                            estadoContenedorRepository.findByNombre("OCUPADO");
                        if (estadoOcupado.isPresent()) {
                            nuevoCont.setEstado(estadoOcupado.get());
                        }
                    } catch (Exception ex) {
                        logger.warn("No se pudo asignar estado OCUPADO al nuevo contenedor: {}", ex.getMessage());
                    }
                    
                    com.backend.tpi.ms_solicitudes.models.Contenedor contGuardado = contenedorService.save(nuevoCont);
                    solicitud.setContenedor(contGuardado);
                    logger.info("Nuevo contenedor creado con ID {} para la solicitud (estado: OCUPADO)", contGuardado.getId());
                }
            } catch (Exception e) {
                logger.error("Error al crear/adjuntar contenedor en la creación de solicitud: {}", e.getMessage());
                throw new IllegalArgumentException("Error al procesar el contenedor: " + e.getMessage());
            }

            // Asignar estado por defecto PENDIENTE (estado inicial de toda solicitud)
            try {
                if (estadoSolicitudRepository != null) {
                    java.util.Optional<com.backend.tpi.ms_solicitudes.models.EstadoSolicitud> estadoPendiente = 
                            estadoSolicitudRepository.findByNombre("PENDIENTE");
                    if (estadoPendiente.isPresent()) {
                        solicitud.setEstado(estadoPendiente.get());
                        logger.info("Estado PENDIENTE asignado a la nueva solicitud - ID Estado: {}", estadoPendiente.get().getId());
                    } else {
                        logger.error("CRITICO: Estado PENDIENTE no encontrado en la base de datos");
                    }
                }
            } catch (Exception e) {
                logger.error("Error al asignar estado por defecto a la solicitud: {}", e.getMessage(), e);
            }

            solicitud = solicitudRepository.save(solicitud);
            logger.info("Solicitud creada exitosamente con ID: {} - Estado: {}", 
                solicitud.getId(), 
                solicitud.getEstado() != null ? solicitud.getEstado().getNombre() : "null");
            return toDto(solicitud);
        }

        /**
         * Obtiene todas las solicitudes sin filtros
         * @return Lista de DTOs con todas las solicitudes
         */
        @org.springframework.transaction.annotation.Transactional(readOnly = true)
        public List<SolicitudDTO> findAll() {
            return solicitudRepository.findAll().stream()
                    .map(this::toDto)
                    .collect(Collectors.toList());
        }

        /**
         * Busca solicitudes aplicando filtros opcionales por estado y/o clienteId
         * @param estado Nombre del estado a filtrar (opcional)
         * @param clienteId ID del cliente a filtrar (opcional)
         * @return Lista de solicitudes que cumplen los criterios
         */
        @org.springframework.transaction.annotation.Transactional(readOnly = true)
        public List<SolicitudDTO> findAllWithFilters(String estado, Long clienteId) {
            logger.debug("Buscando solicitudes con filtros - estado: {}, clienteId: {}", estado, clienteId);
            List<Solicitud> solicitudes;
        
            if (estado != null && !estado.isEmpty() && clienteId != null) {
                // Filtrar por ambos
                solicitudes = solicitudRepository.findByEstado_Nombre(estado).stream()
                        .filter(s -> clienteId.equals(s.getClienteId()))
                        .toList();
            } else if (estado != null && !estado.isEmpty()) {
                // Solo por estado
                solicitudes = solicitudRepository.findByEstado_Nombre(estado);
            } else if (clienteId != null) {
                // Solo por cliente
                solicitudes = solicitudRepository.findByClienteId(clienteId);
            } else {
                // Sin filtros
                solicitudes = solicitudRepository.findAll();
            }
        
            logger.debug("Encontradas {} solicitudes con los filtros aplicados", solicitudes.size());
            return solicitudes.stream()
                    .map(this::toDto)
                    .collect(Collectors.toList());
        }

        /**
         * Busca una solicitud por su ID
         * @param id ID de la solicitud
         * @return DTO de la solicitud encontrada, o null si no existe
         */
        @org.springframework.transaction.annotation.Transactional(readOnly = true)
        public SolicitudDTO findById(Long id) {
            logger.debug("Buscando solicitud por ID: {}", id);
            Optional<Solicitud> solicitud = solicitudRepository.findById(id);
            if (solicitud.isPresent()) {
                logger.debug("Solicitud encontrada con ID: {}", id);
            } else {
                logger.warn("Solicitud no encontrada con ID: {}", id);
            }
            return solicitud.map(this::toDto).orElse(null);
        }

        /**
         * Devuelve el clienteId (propietario) de una solicitud, o null si no existe
         * @param id ID de la solicitud
         * @return clienteId o null
         */
        @org.springframework.transaction.annotation.Transactional(readOnly = true)
        public Long getClienteIdBySolicitudId(Long id) {
            Optional<Solicitud> solicitud = solicitudRepository.findById(id);
            return solicitud.map(Solicitud::getClienteId).orElse(null);
        }

        /**
         * Actualiza una solicitud existente
         * @param id ID de la solicitud a actualizar
         * @param createSolicitudDTO Nuevos datos de la solicitud
         * @return DTO de la solicitud actualizada, o null si no existe
         */
        @org.springframework.transaction.annotation.Transactional
        public SolicitudDTO update(Long id, CreateSolicitudDTO createSolicitudDTO) {
            logger.debug("Actualizando solicitud ID: {}", id);
            Optional<Solicitud> optionalSolicitud = solicitudRepository.findById(id);
            if (optionalSolicitud.isPresent()) {
                Solicitud solicitud = optionalSolicitud.get();
                // manual mapping of updatable fields
                solicitud.setDireccionOrigen(createSolicitudDTO.getDireccionOrigen());
                solicitud.setDireccionDestino(createSolicitudDTO.getDireccionDestino());
                // Validación: no permitir IDs de depósito al actualizar (se debe enviar dirección de texto o coordenadas)
                if (geocodificacionService.isDireccionDeposito(createSolicitudDTO.getDireccionOrigen())) {
                    logger.error("No se permiten IDs de depósito en la dirección de origen al actualizar solicitud: {}", createSolicitudDTO.getDireccionOrigen());
                    throw new IllegalArgumentException("No pasar IDs de depósito en las solicitudes. Enviar dirección de texto o coordenadas.");
                }
                if (geocodificacionService.isDireccionDeposito(createSolicitudDTO.getDireccionDestino())) {
                    logger.error("No se permiten IDs de depósito en la dirección de destino al actualizar solicitud: {}", createSolicitudDTO.getDireccionDestino());
                    throw new IllegalArgumentException("No pasar IDs de depósito en las solicitudes. Enviar dirección de texto o coordenadas.");
                }
                // Geocodificar direcciones a coordenadas
                logger.debug("Geocodificando dirección de origen actualizada: {}", createSolicitudDTO.getDireccionOrigen());
                com.backend.tpi.ms_solicitudes.dtos.CoordenadaDTO coordOrigen = geocodificacionService.geocodificar(
                    createSolicitudDTO.getDireccionOrigen());
                if (coordOrigen != null) {
                    solicitud.setOrigenLat(geocodificacionService.toBigDecimal(coordOrigen.getLatitud()));
                    solicitud.setOrigenLong(geocodificacionService.toBigDecimal(coordOrigen.getLongitud()));
                    logger.info("Coordenadas de origen actualizadas: lat={}, lon={}", 
                        coordOrigen.getLatitud(), coordOrigen.getLongitud());
                } else {
                    logger.warn("No se pudo geocodificar la dirección de origen: {}", createSolicitudDTO.getDireccionOrigen());
                }
                
                logger.debug("Geocodificando dirección de destino actualizada: {}", createSolicitudDTO.getDireccionDestino());
                com.backend.tpi.ms_solicitudes.dtos.CoordenadaDTO coordDestino = geocodificacionService.geocodificar(
                    createSolicitudDTO.getDireccionDestino());
                if (coordDestino != null) {
                    solicitud.setDestinoLat(geocodificacionService.toBigDecimal(coordDestino.getLatitud()));
                    solicitud.setDestinoLong(geocodificacionService.toBigDecimal(coordDestino.getLongitud()));
                    logger.info("Coordenadas de destino actualizadas: lat={}, lon={}", 
                        coordDestino.getLatitud(), coordDestino.getLongitud());
                } else {
                    logger.warn("No se pudo geocodificar la dirección de destino: {}", createSolicitudDTO.getDireccionDestino());
                }
                
                solicitud = solicitudRepository.save(solicitud);
                logger.info("Solicitud ID: {} actualizada exitosamente", id);
                return toDto(solicitud);
            }
            logger.warn("No se pudo actualizar solicitud - ID no encontrado: {}", id);
            return null;
        }

        /**
         * Elimina una solicitud por su ID
         * @param id ID de la solicitud a eliminar
         * @throws RuntimeException si la solicitud tiene una ruta asignada
         */
        @org.springframework.transaction.annotation.Transactional
        public void delete(Long id) {
            logger.info("Eliminando solicitud ID: {}", id);
            
            // Validar que no tenga ruta asignada
            SolicitudDTO solicitudDTO = findById(id);
            if (solicitudDTO != null && solicitudDTO.getRutaId() != null) {
                throw new RuntimeException("No se puede eliminar la solicitud ID " + id + 
                    " porque tiene una ruta asignada (Ruta ID: " + solicitudDTO.getRutaId() + "). " +
                    "Debe eliminar primero la ruta asociada.");
            }
            
            solicitudRepository.deleteById(id);
            logger.debug("Solicitud ID: {} eliminada de la base de datos", id);
        }

        /**
         * Convierte una entidad Solicitud a su DTO
         * @param solicitud Entidad solicitud
         * @return DTO de la solicitud
         */
        // Helper: map entity -> DTO
        private SolicitudDTO toDto(Solicitud solicitud) {
            if (solicitud == null) return null;
            SolicitudDTO dto = new SolicitudDTO();
            dto.setId(solicitud.getId());
            dto.setClienteId(solicitud.getClienteId());
            dto.setDireccionOrigen(solicitud.getDireccionOrigen());
            dto.setDireccionDestino(solicitud.getDireccionDestino());
            // Map coordinates if present
            if (solicitud.getOrigenLat() != null) dto.setOrigenLat(solicitud.getOrigenLat().doubleValue());
            if (solicitud.getOrigenLong() != null) dto.setOrigenLong(solicitud.getOrigenLong().doubleValue());
            if (solicitud.getDestinoLat() != null) dto.setDestinoLat(solicitud.getDestinoLat().doubleValue());
            if (solicitud.getDestinoLong() != null) dto.setDestinoLong(solicitud.getDestinoLong().doubleValue());
            // estado may be null
            if (solicitud.getEstado() != null) dto.setEstado(solicitud.getEstado().getNombre());
            // exponer contenedorId para integraciones (ms-rutas, ms-gestion-calculos)
            if (solicitud.getContenedor() != null && solicitud.getContenedor().getId() != null) {
                dto.setContenedorId(solicitud.getContenedor().getId());
            }
            if (solicitud.getRutaId() != null) dto.setRutaId(solicitud.getRutaId());
            if (solicitud.getTarifaId() != null) dto.setTarifaId(solicitud.getTarifaId());
            // other fields (fechaCreacion, fechaModificacion)
            dto.setFechaCreacion(solicitud.getFechaCreacion());
            dto.setFechaModificacion(solicitud.getFechaModificacion());
            return dto;
        }

        /**
         * Establece la referencia de ruta (rutaId) en una solicitud existente
         * @param solicitudId ID de la solicitud a actualizar
         * @param rutaId ID de la ruta a asociar
         * @return DTO de la solicitud actualizada
         */
        @org.springframework.transaction.annotation.Transactional
        public SolicitudDTO setRutaId(Long solicitudId, Long rutaId) {
            logger.info("Seteando rutaId {} para solicitud {}", rutaId, solicitudId);
            Solicitud solicitud = solicitudRepository.findById(solicitudId)
                    .orElseThrow(() -> {
                        logger.error("No se puede setear rutaId - Solicitud no encontrada con ID: {}", solicitudId);
                        return new RuntimeException("Solicitud no encontrada con ID: " + solicitudId);
                    });
            solicitud.setRutaId(rutaId);
            
            // Cambiar estado a PROGRAMADA cuando se asocia una ruta
            java.util.Optional<com.backend.tpi.ms_solicitudes.models.EstadoSolicitud> estadoProgramadaOpt =
                    estadoSolicitudRepository.findByNombre("PROGRAMADA");
            if (estadoProgramadaOpt.isPresent()) {
                solicitud.setEstado(estadoProgramadaOpt.get());
                logger.info("Estado de solicitud cambiado a PROGRAMADA");
            }
            
            solicitud = solicitudRepository.save(solicitud);
            logger.info("Solicitud ID: {} actualizada con rutaId: {}", solicitudId, rutaId);
            return toDto(solicitud);
        }

        /**
         * Cambia el estado de una solicitud (usado por ms-rutas-transportistas)
         * @param solicitudId ID de la solicitud
         * @param nuevoEstado Nombre del nuevo estado
         * @return Solicitud actualizada
         */
        @org.springframework.transaction.annotation.Transactional
        public SolicitudDTO cambiarEstado(Long solicitudId, String nuevoEstado) {
            logger.info("Cambiando estado de solicitud {} a {}", solicitudId, nuevoEstado);
            Solicitud solicitud = solicitudRepository.findById(solicitudId)
                    .orElseThrow(() -> {
                        logger.error("Solicitud no encontrada con ID: {}", solicitudId);
                        return new RuntimeException("Solicitud no encontrada con ID: " + solicitudId);
                    });
            
            java.util.Optional<com.backend.tpi.ms_solicitudes.models.EstadoSolicitud> estadoOpt =
                    estadoSolicitudRepository.findByNombre(nuevoEstado);
            if (estadoOpt.isPresent()) {
                solicitud.setEstado(estadoOpt.get());
                logger.info("Estado cambiado a {}", estadoOpt.get().getNombre());
            }
            
            solicitud = solicitudRepository.save(solicitud);
            return toDto(solicitud);
        }

        // ----- Integration points (basic implementations) -----
        /**
         * Solicita una ruta al microservicio ms-rutas-transportistas para la solicitud indicada
         * @param solicitudId ID de la solicitud para la cual solicitar la ruta
         * @return Respuesta del microservicio de rutas
         */
        public Object requestRoute(Long solicitudId) {
            return requestRoute(solicitudId, false);
        }

        /**
         * Solicita una ruta al microservicio ms-rutas-transportistas para la solicitud indicada
         * Si persistEstimates=true intentará guardar las estimaciones por tramo en ms-rutas
         */
        public Object requestRoute(Long solicitudId, boolean persistEstimates) {
            logger.info("Solicitando ruta para solicitud ID: {} al microservicio de rutas (persistEstimates={})", solicitudId, persistEstimates);
            Map<String, Object> body = new HashMap<>();
            body.put("idSolicitud", solicitudId);
            try {
                String token = extractBearerToken();
                // usar rutasClient (baseUrl ya configurada)
                ResponseEntity<Map<String, Object>> rutasResp = rutasClient.post()
                    .uri("/api/v1/rutas")
                    .headers(h -> { if (token != null) h.setBearerAuth(token); })
                    .body(body)
                    .retrieve()
                    .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {});
                logger.info("Ruta solicitada exitosamente para solicitud ID: {}", solicitudId);

                Map<String, Object> rutaBody = rutasResp != null ? rutasResp.getBody() : null;
                // Adjuntar estimaciones de costo/tiempo si es posible
                try {
                    Object estimacion = calculatePrice(solicitudId);
                    if (estimacion != null && rutaBody != null) {
                        if (estimacion instanceof com.backend.tpi.ms_solicitudes.dtos.CostoResponseDTO) {
                            com.backend.tpi.ms_solicitudes.dtos.CostoResponseDTO c = (com.backend.tpi.ms_solicitudes.dtos.CostoResponseDTO) estimacion;
                            rutaBody.put("costoEstimado", c.getCostoTotal());
                            rutaBody.put("tiempoEstimado", c.getTiempoEstimado());
                        } else if (estimacion instanceof java.util.Map) {
                            @SuppressWarnings("unchecked")
                            java.util.Map<String, Object> m = (java.util.Map<String, Object>) estimacion;
                            if (m.get("precio") != null) rutaBody.put("costoEstimado", m.get("precio"));
                            if (m.get("tiempo") != null) rutaBody.put("tiempoEstimado", m.get("tiempo"));
                            // También soportar claves alternativas
                            if (m.get("costo") != null) rutaBody.putIfAbsent("costoEstimado", m.get("costo"));
                            if (m.get("tiempoEstimado") != null) rutaBody.putIfAbsent("tiempoEstimado", m.get("tiempoEstimado"));
                        }
                    }
                } catch (Exception e) {
                    logger.warn("No se pudo obtener estimación de costo/tiempo para solicitud {}: {}", solicitudId, e.getMessage());
                }

                // Si la ruta contiene tramos, intentar estimar costo/distancia por cada tramo
                try {
                    if (rutaBody != null && rutaBody.get("tramos") instanceof java.util.List) {
                        @SuppressWarnings("unchecked")
                        java.util.List<java.util.Map<String, Object>> tramos = (java.util.List<java.util.Map<String, Object>>) rutaBody.get("tramos");
                        for (java.util.Map<String, Object> tramo : tramos) {
                            try {
                                // Intentar extraer origen/destino del tramo en varias formas
                                String origenStr = null;
                                String destinoStr = null;

                                // 1) claves lat/lon
                                if (tramo.get("origenLat") != null && tramo.get("origenLong") != null && tramo.get("destinoLat") != null && tramo.get("destinoLong") != null) {
                                    origenStr = tramo.get("origenLat").toString() + "," + tramo.get("origenLong").toString();
                                    destinoStr = tramo.get("destinoLat").toString() + "," + tramo.get("destinoLong").toString();
                                }
                                // 2) claves 'origen'/'destino' como strings
                                if ((origenStr == null || destinoStr == null) && tramo.get("origen") != null && tramo.get("destino") != null) {
                                    origenStr = tramo.get("origen").toString();
                                    destinoStr = tramo.get("destino").toString();
                                }
                                // 3) keys start/end
                                if ((origenStr == null || destinoStr == null) && tramo.get("start") != null && tramo.get("end") != null) {
                                    origenStr = tramo.get("start").toString();
                                    destinoStr = tramo.get("end").toString();
                                }

                                if (origenStr != null && destinoStr != null) {
                                    java.util.Map<String, Object> estimTramo = calculatePriceBetween(origenStr, destinoStr);
                                    if (estimTramo != null) {
                                        // adjuntar valores al tramo (respuesta transitoria)
                                        if (estimTramo.get("precio") != null) tramo.put("costoEstimadoTramo", estimTramo.get("precio"));
                                        if (estimTramo.get("distancia") != null) tramo.put("distanciaTramo", estimTramo.get("distancia"));

                                        // Persistir en ms-rutas solo si se pidió explícitamente
                                        if (persistEstimates) {
                                            try {
                                                // Intentar persistir usando PATCH /api/v1/tramos/{id}
                                                Object idObj = tramo.get("id");
                                                if (idObj != null) {
                                                    Long tramoId;
                                                    if (idObj instanceof Number) tramoId = ((Number) idObj).longValue();
                                                    else tramoId = Long.valueOf(idObj.toString());

                                                    java.util.Map<String, Object> persistBody = new java.util.HashMap<>();
                                                    if (estimTramo.get("precio") != null) persistBody.put("costoEstimado", estimTramo.get("precio"));
                                                    if (estimTramo.get("distancia") != null) persistBody.put("distancia", estimTramo.get("distancia"));

                                                    try {
                                                        rutasClient.patch()
                                                            .uri("/api/v1/tramos/" + tramoId)
                                                            .headers(h -> { if (token != null) h.setBearerAuth(token); })
                                                            .body(persistBody)
                                                            .retrieve()
                                                            .toEntity(Object.class);
                                                    } catch (Exception patchEx) {
                                                        // Fallback: intentar POST /api/v1/tramos/{id}/estimaciones
                                                        try {
                                                            rutasClient.post()
                                                                .uri("/api/v1/tramos/" + tramoId + "/estimaciones")
                                                                .headers(h -> { if (token != null) h.setBearerAuth(token); })
                                                                .body(persistBody)
                                                                .retrieve()
                                                                .toEntity(Object.class);
                                                        } catch (Exception postEx) {
                                                            logger.warn("No se pudo persistir estimaciones para tramo {} en ms-rutas: {} / {}", tramoId, patchEx.getMessage(), postEx.getMessage());
                                                        }
                                                    }
                                                }
                                            } catch (Exception persistEx) {
                                                logger.warn("Error persistiendo estimación de tramo para solicitud {}: {}", solicitudId, persistEx.getMessage());
                                            }
                                        }
                                    }
                                }
                            } catch (Exception exTramo) {
                                logger.warn("No se pudo estimar tramo en ruta solicitud {}: {}", solicitudId, exTramo.getMessage());
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Error calculando estimaciones por tramo para solicitud {}: {}", solicitudId, e.getMessage());
                }

                return rutaBody;
            } catch (Exception e) {
                logger.error("Error al solicitar ruta para solicitud ID: {} - {}", solicitudId, e.getMessage());
                throw e;
            }
        }

        /**
         * Calcula el precio de una solicitud delegando al microservicio ms-gestion-calculos
         * Si falla la llamada remota, utiliza cálculo local como fallback
         * @param solicitudId ID de la solicitud
         * @return Objeto con información del costo calculado
         */
        public Object calculatePrice(Long solicitudId) {
            logger.info("Calculando precio para solicitud ID: {}", solicitudId);
            // Prefer delegar el cálculo al microservicio ms-gestion-calculos
            try {
                // delegar la llamada al cálculo remoto
                logger.debug("Llamando a ms-gestion-calculos para calcular precio de solicitud ID: {}", solicitudId);
                String token = extractBearerToken();
                ResponseEntity<com.backend.tpi.ms_solicitudes.dtos.CostoResponseDTO> costoResp = calculosClient.post()
                    .uri("/api/v1/precio/solicitud/" + solicitudId + "/costo")
                    .headers(h -> { if (token != null) h.setBearerAuth(token); })
                    .retrieve()
                    .toEntity(com.backend.tpi.ms_solicitudes.dtos.CostoResponseDTO.class);
                com.backend.tpi.ms_solicitudes.dtos.CostoResponseDTO resp = costoResp != null ? costoResp.getBody() : null;
                if (resp != null) {
                    logger.info("Precio calculado exitosamente para solicitud ID: {} - Costo total: {}", solicitudId, resp.getCostoTotal());
                    return resp;
                }
            } catch (Exception ex) {
                logger.warn("Falló la llamada remota para calcular precio de solicitud ID: {} - Usando cálculo local como fallback", solicitudId);
                // si falla la llamada remota, caeremos al cálculo local
            }


            Optional<Solicitud> optionalSolicitud = solicitudRepository.findById(solicitudId);
            if (optionalSolicitud.isEmpty()) {
                logger.error("No se puede calcular precio - Solicitud no encontrada con ID: {}", solicitudId);
                throw new IllegalArgumentException("Solicitud not found: " + solicitudId);
            }
            Solicitud solicitud = optionalSolicitud.get();

            // Call calculos to compute distance (via RestClient)
            // Prioridad: coordenadas > dirección de texto
            Map<String, String> distanciaReq = new HashMap<>();
            
            // Determinar origen
            String origen;
            if (solicitud.getOrigenLat() != null && solicitud.getOrigenLong() != null) {
                // Usar coordenadas en formato "lat,lon"
                origen = solicitud.getOrigenLat() + "," + solicitud.getOrigenLong();
                logger.debug("Usando coordenadas de origen: {}", origen);
            } else if (solicitud.getDireccionOrigen() != null) {
                // Fallback: usar dirección de texto (legacy - puede no funcionar)
                origen = solicitud.getDireccionOrigen();
                logger.warn("Solicitud {} sin coordenadas de origen - usando dirección texto (puede fallar): {}", 
                        solicitudId, origen);
            } else {
                logger.error("Solicitud {} no tiene ni coordenadas ni dirección de origen", solicitudId);
                throw new IllegalArgumentException("Solicitud no tiene información de origen");
            }
            
            // Determinar destino
            String destino;
            if (solicitud.getDestinoLat() != null && solicitud.getDestinoLong() != null) {
                // Usar coordenadas en formato "lat,lon"
                destino = solicitud.getDestinoLat() + "," + solicitud.getDestinoLong();
                logger.debug("Usando coordenadas de destino: {}", destino);
            } else if (solicitud.getDireccionDestino() != null) {
                // Fallback: usar dirección de texto (legacy - puede no funcionar)
                destino = solicitud.getDireccionDestino();
                logger.warn("Solicitud {} sin coordenadas de destino - usando dirección texto (puede fallar): {}", 
                        solicitudId, destino);
            } else {
                logger.error("Solicitud {} no tiene ni coordenadas ni dirección de destino", solicitudId);
                throw new IllegalArgumentException("Solicitud no tiene información de destino");
            }
            
            distanciaReq.put("origen", origen);
            distanciaReq.put("destino", destino);

            logger.debug("Calculando distancia para solicitud ID: {} - origen: {}, destino: {}", 
                    solicitudId, origen, destino);
            Map<String, Object> distanciaResp = null;
            String token = extractBearerToken();
            ResponseEntity<Map<String, Object>> distanciaEntity = calculosClient.post()
                .uri("/api/v1/gestion/distancia")
                .headers(h -> { if (token != null) h.setBearerAuth(token); })
                .body(distanciaReq)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {});
            distanciaResp = distanciaEntity != null ? distanciaEntity.getBody() : null;
            Double distancia = null;
            if (distanciaResp != null && distanciaResp.get("distancia") instanceof Number) {
                distancia = ((Number) distanciaResp.get("distancia")).doubleValue();
            }

            // Get tarifas from calculos service (via RestClient)
            List<Map<String, Object>> tarifas = null;
            ResponseEntity<java.util.List<java.util.Map<String, Object>>> tarifasEntity = calculosClient.get()
                .uri("/api/v1/tarifas")
                .headers(h -> { if (token != null) h.setBearerAuth(token); })
                .retrieve()
                .toEntity(new ParameterizedTypeReference<java.util.List<java.util.Map<String, Object>>>() {});
            tarifas = tarifasEntity != null ? tarifasEntity.getBody() : null;

            Double precioPorKm = null;
            if (tarifas != null && !tarifas.isEmpty()) {
                Object maybePrecio = tarifas.get(0).get("precioPorKm");
                if (maybePrecio instanceof Number) {
                    precioPorKm = ((Number) maybePrecio).doubleValue();
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("solicitudId", solicitudId);
            result.put("distancia", distancia);
            if (distancia != null && precioPorKm != null) {
                result.put("precio", distancia * precioPorKm);
                result.put("precioPorKm", precioPorKm);
            } else {
                result.put("precio", null);
                result.put("precioPorKm", precioPorKm);
            }
            return result;
        }

        /**
         * Asigna un transportista (camión) a una solicitud delegando al microservicio ms-rutas-transportistas
         * Pasos: 1) Busca la ruta por solicitudId, 2) Asigna el camión a un tramo de esa ruta
         * @param solicitudId ID de la solicitud
         * @param transportistaId ID del camión/transportista a asignar
         * @return Respuesta del microservicio de rutas
         */
        public Object assignTransport(Long solicitudId, Long transportistaId) {
            logger.info("Asignando transportista ID: {} a solicitud ID: {}", transportistaId, solicitudId);
            // 1) find route for solicitud
            logger.debug("Buscando ruta para solicitud ID: {}", solicitudId);
            Map<String, Object> ruta = null;
            String token = extractBearerToken();
            ResponseEntity<Map<String, Object>> rutaEntity = rutasClient.get()
                .uri("/api/v1/rutas/por-solicitud/{id}", solicitudId)
                .headers(h -> { if (token != null) h.setBearerAuth(token); })
                .retrieve()
                .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {});
            ruta = rutaEntity != null ? rutaEntity.getBody() : null;
            if (ruta == null || ruta.get("id") == null) {
                logger.error("No se encontró ruta para solicitud ID: {}", solicitudId);
                throw new IllegalArgumentException("No ruta found for solicitud: " + solicitudId);
            }
            Object rutaIdObj = ruta.get("id");
            Long rutaId;
            if (rutaIdObj instanceof Number) rutaId = ((Number) rutaIdObj).longValue();
            else rutaId = Long.valueOf(rutaIdObj.toString());
            logger.debug("Ruta encontrada con ID: {} para solicitud ID: {}", rutaId, solicitudId);

            // 2) get tramos for the route and pick an unassigned tramo
            logger.debug("Buscando tramos para ruta ID: {}", rutaId);
            java.util.List<Map<String, Object>> tramos = null;
            ResponseEntity<java.util.List<java.util.Map<String, Object>>> tramosEntity = rutasClient.get()
                .uri("/api/v1/tramos/por-ruta/{id}", rutaId)
                .headers(h -> { if (token != null) h.setBearerAuth(token); })
                .retrieve()
                .toEntity(new ParameterizedTypeReference<java.util.List<java.util.Map<String, Object>>>() {});
            tramos = tramosEntity != null ? tramosEntity.getBody() : null;
            if (tramos == null || tramos.isEmpty()) {
                logger.error("No se encontraron tramos para ruta ID: {}", rutaId);
                throw new IllegalArgumentException("No tramos found for ruta: " + rutaId);
            }

            Long tramoIdToAssign = null;
            for (Map<String, Object> t : tramos) {
                Object camionDominio = t.get("camionDominio");
                if (camionDominio == null) {
                    Object idObj = t.get("id");
                    if (idObj instanceof Number) tramoIdToAssign = ((Number) idObj).longValue();
                    else tramoIdToAssign = Long.valueOf(idObj.toString());
                    logger.debug("Tramo sin asignar encontrado - ID: {}", tramoIdToAssign);
                    break;
                }
            }
            if (tramoIdToAssign == null) {
                // no unassigned tramo found; pick the first tramo
                Object idObj = tramos.get(0).get("id");
                if (idObj instanceof Number) tramoIdToAssign = ((Number) idObj).longValue();
                else tramoIdToAssign = Long.valueOf(idObj.toString());
                logger.debug("No hay tramos sin asignar, usando el primero - ID: {}", tramoIdToAssign);
            }

            // 3) call assign endpoint for the tramo
            logger.debug("Asignando transportista ID: {} al tramo ID: {}", transportistaId, tramoIdToAssign);
            ResponseEntity<Object> assignResp = rutasClient.post()
                .uri("/api/v1/tramos/" + tramoIdToAssign + "/asignar-transportista?camionId=" + transportistaId)
                .headers(h -> { if (token != null) h.setBearerAuth(token); })
                .retrieve()
                .toEntity(Object.class);
            logger.info("Transportista ID: {} asignado exitosamente a solicitud ID: {}", transportistaId, solicitudId);
            return assignResp != null ? assignResp.getBody() : null;
        }

        /**
         * Actualiza el estado de una solicitud con validación de transición
         * @param id ID de la solicitud
         * @param estadoId ID del nuevo estado
         * @return DTO de la solicitud actualizada
         * @throws IllegalStateException si la transición no es válida
         */
        @org.springframework.transaction.annotation.Transactional
        public SolicitudDTO updateEstado(Long id, Long estadoId) {
            logger.info("Actualizando estado de solicitud ID: {} a estado ID: {}", id, estadoId);
            Solicitud solicitud = solicitudRepository.findById(id)
                    .orElseThrow(() -> {
                        logger.error("No se puede actualizar estado - Solicitud no encontrada con ID: {}", id);
                        return new RuntimeException("Solicitud no encontrada con ID: " + id);
                    });
        
            // Obtener el estado destino
            com.backend.tpi.ms_solicitudes.models.EstadoSolicitud estadoDestino = estadoSolicitudRepository.findById(estadoId)
                .orElseThrow(() -> {
                    logger.error("Estado no encontrado con ID: {}", estadoId);
                    return new IllegalArgumentException("Estado no encontrado con ID: " + estadoId);
                });
            
            // Validar transición si hay estado actual
            if (solicitud.getEstado() != null) {
                String estadoOrigenNombre = solicitud.getEstado().getNombre();
                String estadoDestinoNombre = estadoDestino.getNombre();
                
                if (!estadoTransicionService.esTransicionSolicitudValida(estadoOrigenNombre, estadoDestinoNombre)) {
                    logger.error("Transición de estado inválida de {} a {}", estadoOrigenNombre, estadoDestinoNombre);
                    throw new IllegalStateException(
                        String.format("No se puede cambiar el estado de '%s' a '%s'. Transición no permitida.", 
                            estadoOrigenNombre, estadoDestinoNombre)
                    );
                }
                logger.debug("Transición válida de {} a {}", estadoOrigenNombre, estadoDestinoNombre);
            }
            
            solicitud.setEstado(estadoDestino);
        
            solicitud = solicitudRepository.save(solicitud);
            logger.info("Estado de solicitud ID: {} actualizado exitosamente", id);
            return toDto(solicitud);
        }

        // `programar` behavior removed: route selection (confirmRouteSelectionByOptionId or setRutaId)
        // now sets the solicitud to PROGRAMADA. This method was intentionally deleted.
        /**
         * Parsea un string como "2h 30m" o "1h" a horas en BigDecimal (ej. "2h 30m" -> 2.5)
         */
        private java.math.BigDecimal parseTiempoEstimadoStringToHours(String tiempo) {
            if (tiempo == null) return null;
            try {
                String s = tiempo.trim().toLowerCase();
                int horas = 0;
                int minutos = 0;
                java.util.regex.Matcher mH = java.util.regex.Pattern.compile("(\\d+)h").matcher(s);
                if (mH.find()) horas = Integer.parseInt(mH.group(1));
                java.util.regex.Matcher mM = java.util.regex.Pattern.compile("(\\d+)m").matcher(s);
                if (mM.find()) minutos = Integer.parseInt(mM.group(1));
                double totalHoras = horas + (minutos / 60.0);
                return java.math.BigDecimal.valueOf(totalHoras).setScale(2, java.math.RoundingMode.HALF_UP);
            } catch (Exception e) {
                logger.warn("No se pudo parsear tiempoEstimado='{}' a horas: {}", tiempo, e.getMessage());
                return null;
            }
        }

    /**
     * Persiste el costo final y tiempo real de la solicitud
     */
    @org.springframework.transaction.annotation.Transactional
    public SolicitudDTO finalizar(Long id, java.math.BigDecimal costoFinal, java.math.BigDecimal tiempoReal) {
        logger.info("Finalizando solicitud ID: {} con costoFinal: {}, tiempoReal: {}", id, costoFinal, tiempoReal);
        Solicitud solicitud = solicitudRepository.findById(id)
                .orElseThrow(() -> {
                    logger.error("No se puede finalizar - Solicitud no encontrada con ID: {}", id);
                    return new RuntimeException("Solicitud no encontrada con ID: " + id);
                });

        if (costoFinal != null) solicitud.setCostoFinal(costoFinal);
        if (tiempoReal != null) solicitud.setTiempoReal(tiempoReal);

        solicitud = solicitudRepository.save(solicitud);
        logger.info("Solicitud ID: {} actualizada con costo final y tiempo real", id);
        return toDto(solicitud);
    }

    /**
     * Consulta los estados a los que puede transicionar una solicitud desde su estado actual
     * @param id ID de la solicitud
     * @return Lista de nombres de estados permitidos
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<String> getEstadosPermitidos(Long id) {
        logger.info("Consultando estados permitidos para solicitud ID: {}", id);
        Solicitud solicitud = solicitudRepository.findById(id)
                .orElseThrow(() -> {
                    logger.error("Solicitud no encontrada con ID: {}", id);
                    return new RuntimeException("Solicitud no encontrada con ID: " + id);
                });
        
        if (solicitud.getEstado() == null) {
            logger.warn("La solicitud ID: {} no tiene estado actual asignado", id);
            return List.of(); // Sin estado actual, no hay transiciones
        }
        
        String estadoActual = solicitud.getEstado().getNombre();
        List<String> permitidos = estadoTransicionService.getEstadosPermitidosSolicitud(estadoActual);
        logger.info("Solicitud ID: {} en estado '{}' puede transicionar a: {}", id, estadoActual, permitidos);
        return permitidos;
    }

    /**
     * Asigna un contenedor a una solicitud validando disponibilidad localmente
     * @param solicitudId ID de la solicitud
     * @param contenedorId ID del contenedor a asignar
     * @return DTO de la solicitud actualizada
     */
    @org.springframework.transaction.annotation.Transactional
    public SolicitudDTO assignContenedor(Long solicitudId, Long contenedorId) {
        logger.info("Asignando contenedor ID: {} a solicitud ID: {}", contenedorId, solicitudId);

        Solicitud solicitud = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada con ID: " + solicitudId));

        // Validar contenedor localmente
        com.backend.tpi.ms_solicitudes.models.Contenedor contenedor = contenedorService.findById(contenedorId);
        if (contenedor.getEstado() == null || contenedor.getEstado().getNombre() == null ||
                !contenedor.getEstado().getNombre().equalsIgnoreCase("LIBRE")) {
            logger.error("Contenedor ID: {} no está disponible (estado: {})", contenedorId,
                    contenedor.getEstado() != null ? contenedor.getEstado().getNombre() : "null");
            throw new IllegalStateException("Contenedor no disponible para asignación: " + contenedorId);
        }



        // Asignar (establecer relación ManyToOne)
        solicitud.setContenedor(contenedor);
        solicitud = solicitudRepository.save(solicitud);

        // Actualizar estado del contenedor a ASIGNADO
        try {
            com.backend.tpi.ms_solicitudes.models.EstadoContenedor estadoAsignado = estadoTransicionService.getEstadoContenedorByNombre("ASIGNADO");
            if (estadoAsignado != null && estadoAsignado.getId() != null) {
                contenedorService.updateEstado(contenedorId, estadoAsignado.getId());
            }
        } catch (Exception e) {
            logger.warn("No se pudo cambiar estado del contenedor {} a ASIGNADO: {}", contenedorId, e.getMessage());
        }

        logger.info("Contenedor ID: {} asignado a solicitud ID: {} exitosamente", contenedorId, solicitudId);
        return toDto(solicitud);
    }

    /**
     * Helper: extrae token Bearer del SecurityContext si existe
     */
    private String extractBearerToken() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken) {
            return ((JwtAuthenticationToken) auth).getToken().getTokenValue();
        }
        return null;
    }

    /**
     * Calcula precio y distancia entre dos puntos (origen,destino) usando ms-gestion-calculos
     * @param origen formato "lat,lon" o dirección de texto
     * @param destino formato "lat,lon" o dirección de texto
     * @return mapa con keys: distancia (Double), precio (Double), precioPorKm (Double) o null si falla
     */
    private java.util.Map<String, Object> calculatePriceBetween(String origen, String destino) {
        try {
            if (origen == null || destino == null) return null;
            Map<String, String> distanciaReq = new HashMap<>();
            distanciaReq.put("origen", origen);
            distanciaReq.put("destino", destino);

            String token = extractBearerToken();
            ResponseEntity<Map<String, Object>> distanciaEntity = calculosClient.post()
                .uri("/api/v1/gestion/distancia")
                .headers(h -> { if (token != null) h.setBearerAuth(token); })
                .body(distanciaReq)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {});
            Map<String, Object> distanciaResp = distanciaEntity != null ? distanciaEntity.getBody() : null;
            Double distancia = null;
            if (distanciaResp != null && distanciaResp.get("distancia") instanceof Number) {
                distancia = ((Number) distanciaResp.get("distancia")).doubleValue();
            }

            ResponseEntity<java.util.List<java.util.Map<String, Object>>> tarifasEntity = calculosClient.get()
                .uri("/api/v1/tarifas")
                .headers(h -> { if (token != null) h.setBearerAuth(token); })
                .retrieve()
                .toEntity(new ParameterizedTypeReference<java.util.List<java.util.Map<String, Object>>>() {});
            java.util.List<java.util.Map<String, Object>> tarifas = tarifasEntity != null ? tarifasEntity.getBody() : null;

            Double precioPorKm = null;
            if (tarifas != null && !tarifas.isEmpty()) {
                Object maybePrecio = tarifas.get(0).get("precioPorKm");
                if (maybePrecio instanceof Number) precioPorKm = ((Number) maybePrecio).doubleValue();
            }

            java.util.Map<String, Object> result = new java.util.HashMap<>();
            result.put("distancia", distancia);
            if (distancia != null && precioPorKm != null) {
                result.put("precio", distancia * precioPorKm);
                result.put("precioPorKm", precioPorKm);
            } else {
                result.put("precio", null);
                result.put("precioPorKm", precioPorKm);
            }
            return result;
        } catch (Exception e) {
            logger.warn("Error al calcular precio entre {} y {}: {}", origen, destino, e.getMessage());
            return null;
        }
    }

    

    /**
     * Confirma la selección basada en una opción ya persistida (opcionId).
     * Asume que la opcionId corresponde a una ruta tentativa en ms-rutas; llama a ms-rutas
     * para confirmar/convertirla en ruta final y asocia el resultado a la solicitud.
     */
    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> confirmRouteSelectionByOptionId(Long solicitudId, Long opcionId) {
        logger.info("Confirmando ruta por opcionId {} para solicitud {}", opcionId, solicitudId);
        if (opcionId == null) throw new IllegalArgumentException("opcionId no puede ser null");
        String token = extractBearerToken();
        Map<String, Object> body = new HashMap<>();
        body.put("idSolicitud", solicitudId);
        try {
            // Intentar confirmar la ruta existente en ms-rutas
            ResponseEntity<Map<String, Object>> resp = rutasClient.post()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/rutas/" + opcionId).queryParam("confirm", "true").build())
                .headers(h -> { if (token != null) h.setBearerAuth(token); })
                .body(body)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {});

            Map<String, Object> respBody = resp != null ? resp.getBody() : null;
            if (respBody != null && respBody.get("id") != null) {
                Object idObj = respBody.get("id");
                Long rutaId;
                if (idObj instanceof Number) rutaId = ((Number) idObj).longValue();
                else rutaId = Long.valueOf(idObj.toString());
                try { setRutaId(solicitudId, rutaId); } catch (Exception e) { logger.warn("No se pudo setRutaId: {}", e.getMessage()); }
            }
            logger.info("Confirmación por opcionId completada para solicitud {} opcion {}", solicitudId, opcionId);
            return respBody;
        } catch (Exception e) {
            logger.error("Error confirmando por opcionId {} para solicitud {}: {}", opcionId, solicitudId, e.getMessage());
            throw e;
        }
    }

    /**
     * Obtiene el seguimiento completo de un contenedor buscando su última solicitud
     * @param contenedorId ID del contenedor a rastrear
     * @return Map con información completa de la solicitud, contenedor y tramos
     */
    public Map<String, Object> getSeguimientoByContenedor(Long contenedorId) {
        logger.info("Buscando última solicitud para contenedor ID: {}", contenedorId);
        
        // Buscar la última solicitud asociada al contenedor
        Optional<Solicitud> solicitudOpt = solicitudRepository.findFirstByContenedor_IdOrderByIdDesc(contenedorId);
        
        if (solicitudOpt.isEmpty()) {
            throw new RuntimeException("No se encontró ninguna solicitud para el contenedor ID: " + contenedorId);
        }
        
        Solicitud solicitud = solicitudOpt.get();
        logger.info("Última solicitud encontrada: ID {}, Estado: {}", 
            solicitud.getId(), 
            solicitud.getEstado() != null ? solicitud.getEstado().getNombre() : "null");
        
        Map<String, Object> resultado = new HashMap<>();
        
        // Información básica de la solicitud
        resultado.put("solicitudId", solicitud.getId());
        resultado.put("estadoSolicitud", solicitud.getEstado() != null ? solicitud.getEstado().getNombre() : null);
        resultado.put("origenDireccion", solicitud.getDireccionOrigen());
        resultado.put("destinoDireccion", solicitud.getDireccionDestino());
        resultado.put("costoFinal", solicitud.getCostoFinal());
        resultado.put("tiempoReal", solicitud.getTiempoReal());
        resultado.put("rutaId", solicitud.getRutaId());
        
        // Información del contenedor
        if (solicitud.getContenedor() != null) {
            Map<String, Object> contenedorInfo = new HashMap<>();
            contenedorInfo.put("id", solicitud.getContenedor().getId());
            contenedorInfo.put("peso", solicitud.getContenedor().getPeso());
            contenedorInfo.put("volumen", solicitud.getContenedor().getVolumen());
            contenedorInfo.put("estado", solicitud.getContenedor().getEstado() != null ? 
                solicitud.getContenedor().getEstado().getNombre() : null);
            resultado.put("contenedor", contenedorInfo);
        }
        
        // Si hay rutaId, consultar los tramos desde ms-rutas-transportistas
        if (solicitud.getRutaId() != null) {
            try {
                String token = extractBearerToken();
                ResponseEntity<Map<String, Object>> rutaResp = rutasClient.get()
                    .uri("/api/v1/rutas/" + solicitud.getRutaId())
                    .headers(h -> { if (token != null) h.setBearerAuth(token); })
                    .retrieve()
                    .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {});
                
                Map<String, Object> rutaData = rutaResp.getBody();
                if (rutaData != null) {
                    resultado.put("ruta", rutaData);
                    logger.info("Información de ruta {} agregada al seguimiento", solicitud.getRutaId());
                }
            } catch (Exception e) {
                logger.warn("No se pudo obtener información de la ruta {}: {}", solicitud.getRutaId(), e.getMessage());
                resultado.put("ruta", null);
            }
        }
        
        // Agregar seguimiento del contenedor (ubicación actual, depósito, etc.)
        try {
            com.backend.tpi.ms_solicitudes.dtos.SeguimientoContenedorDTO seguimientoContenedor = 
                contenedorService.getSeguimiento(contenedorId);
            
            Map<String, Object> ubicacion = new HashMap<>();
            ubicacion.put("latitud", seguimientoContenedor.getUbicacionActualLat());
            ubicacion.put("longitud", seguimientoContenedor.getUbicacionActualLong());
            ubicacion.put("depositoId", seguimientoContenedor.getDepositoId());
            ubicacion.put("estadoContenedor", seguimientoContenedor.getEstadoActual());
            
            resultado.put("ubicacionActual", ubicacion);
        } catch (Exception e) {
            logger.warn("No se pudo obtener seguimiento del contenedor: {}", e.getMessage());
        }
        
        return resultado;
    }
}

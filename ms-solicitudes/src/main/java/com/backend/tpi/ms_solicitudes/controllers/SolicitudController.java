package com.backend.tpi.ms_solicitudes.controllers;

import com.backend.tpi.ms_solicitudes.dtos.CreateSolicitudDTO;
import com.backend.tpi.ms_solicitudes.dtos.SeguimientoSolicitudDTO;
import com.backend.tpi.ms_solicitudes.dtos.SolicitudDTO;
import com.backend.tpi.ms_solicitudes.services.SolicitudService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.backend.tpi.ms_solicitudes.services.ClienteService;
import com.backend.tpi.ms_solicitudes.models.Cliente;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador REST para gestionar Solicitudes de transporte
 * Expone endpoints CRUD y operaciones de integración con otros microservicios
 */
@RestController
@RequestMapping("/api/v1/solicitudes")
public class SolicitudController {

    private static final Logger logger = LoggerFactory.getLogger(SolicitudController.class);

    @Autowired
    private SolicitudService solicitudService;

    @Autowired
    private ClienteService clienteService;
    
    @Autowired
    private com.backend.tpi.ms_solicitudes.services.ContenedorService contenedorService;

    /**
     * POST /api/v1/solicitudes - Crea una nueva solicitud de transporte
     * Requiere rol CLIENTE
     * @param createSolicitudDTO Datos de la solicitud a crear
     * @return Solicitud creada con código 200
     */
    @PostMapping
    // Endpoint público para que un cliente pueda registrar una solicitud y crear usuario
    public ResponseEntity<?> create(@jakarta.validation.Valid @RequestBody CreateSolicitudDTO createSolicitudDTO,
                                    org.springframework.validation.BindingResult bindingResult) {
        logger.info("POST /api/v1/solicitudes - Creando nueva solicitud");

        // Bean validation errors
        if (bindingResult.hasErrors()) {
            java.util.Map<String, String> errors = new java.util.HashMap<>();
            bindingResult.getFieldErrors().forEach(fe -> errors.put(fe.getField(), fe.getDefaultMessage()));
            logger.warn("POST /api/v1/solicitudes - Validation errors: {}", errors);
            return ResponseEntity.badRequest().body(errors);
        }

        // Conditional validation: if no contenedorId provided, require peso and volumen > 0
        if (createSolicitudDTO.getContenedorId() == null) {
            if (createSolicitudDTO.getContenedorPeso() == null || createSolicitudDTO.getContenedorVolumen() == null) {
                return ResponseEntity.badRequest().body(java.util.Map.of("contenedor", "Si no se provee contenedorId, contenedorPeso y contenedorVolumen son obligatorios"));
            }
            if (createSolicitudDTO.getContenedorPeso().doubleValue() <= 0 || createSolicitudDTO.getContenedorVolumen().doubleValue() <= 0) {
                return ResponseEntity.badRequest().body(java.util.Map.of("contenedor", "contenedorPeso y contenedorVolumen deben ser mayores a 0"));
            }
        }

        SolicitudDTO result = solicitudService.create(createSolicitudDTO);
        logger.info("POST /api/v1/solicitudes - Respuesta: 200 - Solicitud creada con ID: {}", result.getId());

        return ResponseEntity.ok(result);
    }

    /**
    * GET /api/v1/solicitudes - Obtiene lista de solicitudes con filtros opcionales
    * Requiere rol OPERADOR o ADMIN
     * @param estado Filtro por nombre de estado (opcional)
     * @param clienteId Filtro por ID de cliente (opcional)
     * @return Lista de solicitudes filtradas
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('OPERADOR','ADMIN','CLIENTE')")
    public ResponseEntity<List<SolicitudDTO>> findAll(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Long clienteId) {
        logger.info("GET /api/v1/solicitudes - Consultando solicitudes con filtros - estado: {}, clienteId: {}", estado, clienteId);

        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean isCliente = auth != null && auth.getAuthorities() != null && 
                           auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CLIENTE"));
        boolean isAdminOrOperador = auth != null && auth.getAuthorities() != null && 
                                    auth.getAuthorities().stream().anyMatch(a -> 
                                        a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_OPERADOR"));
        
        if (isCliente && !isAdminOrOperador) {
            // Si el caller es CLIENTE (sin ser admin/operador), forzamos clienteId al del token
            if (auth instanceof JwtAuthenticationToken) {
                Object emailObj = ((JwtAuthenticationToken) auth).getToken().getClaim("email");
                String email = emailObj != null ? emailObj.toString() : null;
                if (email != null) {
                    try {
                        Cliente c = clienteService.findByEmail(email);
                        clienteId = c.getId();
                    } catch (Exception ex) {
                        logger.warn("No se pudo validar cliente por email {}: {}", email, ex.getMessage());
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
                    }
                }
            }
        }

        List<SolicitudDTO> result = solicitudService.findAllWithFilters(estado, clienteId);
        logger.info("GET /api/v1/solicitudes - Respuesta: 200 - {} solicitudes encontradas", result.size());
        return ResponseEntity.ok(result);
    }

    /**
    * GET /api/v1/solicitudes/{id} - Obtiene una solicitud específica por ID
    * Requiere rol CLIENTE, OPERADOR o ADMIN
     * @param id ID de la solicitud
     * @return Solicitud encontrada (200) o Not Found (404)
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLIENTE','OPERADOR','ADMIN')")
    public ResponseEntity<SolicitudDTO> findById(@PathVariable Long id) {
        logger.info("GET /api/v1/solicitudes/{} - Buscando solicitud por ID", id);
        SolicitudDTO solicitudDTO = solicitudService.findById(id);
        if (solicitudDTO == null) {
            logger.warn("GET /api/v1/solicitudes/{} - Respuesta: 404 - Solicitud no encontrada", id);
            return ResponseEntity.notFound().build();
        }
        // Si el caller es CLIENTE verificamos propiedad (solo si NO tiene roles de admin/operador)
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities() != null) {
            boolean hasCliente = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CLIENTE"));
            boolean hasAdminOrOperador = auth.getAuthorities().stream().anyMatch(a -> 
                a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_OPERADOR"));
            
            if (hasCliente && !hasAdminOrOperador) {
                if (auth instanceof JwtAuthenticationToken) {
                    Object emailObj = ((JwtAuthenticationToken) auth).getToken().getClaim("email");
                    String email = emailObj != null ? emailObj.toString() : null;
                    if (email != null) {
                        try {
                            Cliente c = clienteService.findByEmail(email);
                            Long ownerId = solicitudService.getClienteIdBySolicitudId(id);
                            if (ownerId == null || !ownerId.equals(c.getId())) {
                                logger.warn("CLIENTE (email={}) intento acceder a solicitud ajena: {}", email, id);
                                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
                            }
                        } catch (Exception ex) {
                            logger.warn("No se pudo validar cliente por email {}: {}", email, ex.getMessage());
                            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
                        }
                    }
                }
            }
        }

        logger.info("GET /api/v1/solicitudes/{} - Respuesta: 200 - Solicitud encontrada", id);
        return ResponseEntity.ok(solicitudDTO);
    }

    /**
    * PUT /api/v1/solicitudes/{id} - Actualiza los datos de una solicitud
    * Requiere rol OPERADOR o ADMIN
     * @param id ID de la solicitud a actualizar
     * @param createSolicitudDTO Nuevos datos de la solicitud
     * @return Solicitud actualizada (200) con detalles de la operación
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERADOR','ADMIN')")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody CreateSolicitudDTO createSolicitudDTO) {
        logger.info("PUT /api/v1/solicitudes/{} - Actualizando solicitud", id);
        
        // Buscar la solicitud
        SolicitudDTO solicitudDTO = solicitudService.findById(id);
        if (solicitudDTO == null) {
            logger.warn("PUT /api/v1/solicitudes/{} - Respuesta: 404 - Solicitud no encontrada", id);
            return ResponseEntity.notFound().build();
        }
        
        // Respuesta con detalles de la operación
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        java.util.List<String> warnings = new java.util.ArrayList<>();
        boolean clienteModificado = false;
        boolean direccionesActualizadas = false;
        
        // 1. Actualizar información del cliente
        try {
            Cliente clienteExistente = clienteService.findById(solicitudDTO.getClienteId());
            Cliente datosCliente = new Cliente();
            datosCliente.setNombre(createSolicitudDTO.getClienteNombre());
            datosCliente.setEmail(createSolicitudDTO.getClienteEmail());
            datosCliente.setTelefono(createSolicitudDTO.getClienteTelefono());
            
            clienteService.update(clienteExistente.getId(), datosCliente);
            clienteModificado = true;
            logger.info("PUT /api/v1/solicitudes/{} - Cliente actualizado correctamente", id);
        } catch (Exception e) {
            logger.warn("PUT /api/v1/solicitudes/{} - Error al actualizar cliente: {}", id, e.getMessage());
            warnings.add("No se pudo actualizar la información del cliente: " + e.getMessage());
        }
        
        // 2. Actualizar direcciones solo si está en estado PROGRAMADA
        String estadoActual = solicitudDTO.getEstado();
        if ("PROGRAMADA".equalsIgnoreCase(estadoActual)) {
            try {
                solicitudDTO = solicitudService.update(id, createSolicitudDTO);
                direccionesActualizadas = true;
                logger.info("PUT /api/v1/solicitudes/{} - Direcciones actualizadas correctamente", id);
            } catch (Exception e) {
                logger.warn("PUT /api/v1/solicitudes/{} - Error al actualizar direcciones: {}", id, e.getMessage());
                warnings.add("No se pudieron actualizar las direcciones: " + e.getMessage());
            }
        } else {
            warnings.add("Las direcciones no se pueden modificar porque la solicitud no está en estado PROGRAMADA (estado actual: " + estadoActual + ")");
            logger.warn("PUT /api/v1/solicitudes/{} - No se actualizaron direcciones: estado {} != PROGRAMADA", id, estadoActual);
        }
        
        // Preparar respuesta
        response.put("solicitud", solicitudDTO);
        response.put("clienteActualizado", clienteModificado);
        response.put("direccionesActualizadas", direccionesActualizadas);
        if (!warnings.isEmpty()) {
            response.put("warnings", warnings);
        }
        
        logger.info("PUT /api/v1/solicitudes/{} - Respuesta: 200 - Actualización completada (cliente: {}, direcciones: {})", 
                    id, clienteModificado, direccionesActualizadas);
        return ResponseEntity.ok(response);
    }

    /**
    * DELETE /api/v1/solicitudes/{id} - Elimina una solicitud
    * Requiere rol OPERADOR o ADMIN
     * @param id ID de la solicitud a eliminar
     * @return No Content (204)
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERADOR','ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        logger.info("DELETE /api/v1/solicitudes/{} - Eliminando solicitud", id);
        solicitudService.delete(id);
        logger.info("DELETE /api/v1/solicitudes/{} - Respuesta: 204 - Solicitud eliminada", id);
        return ResponseEntity.noContent().build();
    }

    /**
    * POST /api/v1/solicitudes/{id}/asignar-contenedor - Asigna un contenedor a la solicitud (manual)
    * Requiere rol OPERADOR o ADMIN
     */
    @PostMapping("/{id}/asignar-contenedor")
    @PreAuthorize("hasAnyRole('OPERADOR','ADMIN')")
    public ResponseEntity<SolicitudDTO> assignContenedor(@PathVariable Long id, @RequestParam Long contenedorId) {
        logger.info("POST /api/v1/solicitudes/{}/asignar-contenedor - Asignando contenedor ID: {}", id, contenedorId);
        try {
            SolicitudDTO result = solicitudService.assignContenedor(id, contenedorId);
            logger.info("POST /api/v1/solicitudes/{}/asignar-contenedor - Respuesta: 200 - Contenedor asignado", id);
            return ResponseEntity.ok(result);
        } catch (IllegalStateException e) {
            logger.warn("POST /api/v1/solicitudes/{}/asignar-contenedor - Contenedor no disponible: {}", id, e.getMessage());
            return ResponseEntity.status(409).build();
        } catch (Exception e) {
            logger.error("POST /api/v1/solicitudes/{}/asignar-contenedor - Error: {}", id, e.getMessage());
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * GET /api/v1/solicitudes/{id}/seguimiento - Devuelve información de seguimiento de la solicitud
     * Incluye estado de la solicitud y, si tiene contenedor asignado, también información del contenedor.
     */
    @GetMapping("/{id}/seguimiento")
    @PreAuthorize("hasAnyRole('CLIENTE','OPERADOR','ADMIN')")
    public ResponseEntity<SeguimientoSolicitudDTO> getSeguimientoSolicitud(@PathVariable Long id) {
        logger.info("GET /api/v1/solicitudes/{}/seguimiento - Consultando seguimiento de solicitud", id);
        SolicitudDTO solicitud = solicitudService.findById(id);
        if (solicitud == null) {
            logger.warn("GET /api/v1/solicitudes/{}/seguimiento - Solicitud no encontrada", id);
            return ResponseEntity.notFound().build();
        }

        SeguimientoSolicitudDTO seguimiento = new SeguimientoSolicitudDTO();
        
        // Información de la solicitud
        seguimiento.setSolicitudId(solicitud.getId());
        seguimiento.setEstadoSolicitud(solicitud.getEstado());
        seguimiento.setRutaId(solicitud.getRutaId());
        seguimiento.setOrigenLat(solicitud.getOrigenLat());
        seguimiento.setOrigenLong(solicitud.getOrigenLong());
        seguimiento.setDestinoLat(solicitud.getDestinoLat());
        seguimiento.setDestinoLong(solicitud.getDestinoLong());

        // Si hay contenedor asignado, agregar su información
        if (solicitud.getContenedorId() != null) {
            try {
                var seguimientoContenedor = contenedorService.getSeguimiento(solicitud.getContenedorId());
                seguimiento.setContenedorId(seguimientoContenedor.getIdContenedor());
                seguimiento.setEstadoContenedor(seguimientoContenedor.getEstadoActual());
                seguimiento.setUbicacionActualLat(seguimientoContenedor.getUbicacionActualLat());
                seguimiento.setUbicacionActualLong(seguimientoContenedor.getUbicacionActualLong());
                seguimiento.setDepositoId(seguimientoContenedor.getDepositoId());
            } catch (Exception e) {
                logger.warn("Error obteniendo seguimiento del contenedor {}: {}", solicitud.getContenedorId(), e.getMessage());
                // Continuar sin información del contenedor
            }
        }

        return ResponseEntity.ok(seguimiento);
    }

    // ---- Integration endpoints (delegan al service) ----

    /**
    * POST /api/v1/solicitudes/{id}/calcular-precio - Calcula el precio de una solicitud
    * Requiere rol OPERADOR o ADMIN
     * @param id ID de la solicitud
     * @return Información de costos calculados
     */
    @PostMapping("/{id}/calcular-precio")
    @PreAuthorize("hasAnyRole('OPERADOR','ADMIN')")
    public ResponseEntity<Object> calculatePrice(@PathVariable Long id) {
        logger.info("POST /api/v1/solicitudes/{}/calcular-precio - Calculando precio", id);
        Object result = solicitudService.calculatePrice(id);
        logger.info("POST /api/v1/solicitudes/{}/calcular-precio - Respuesta: 200 - Precio calculado", id);
        return ResponseEntity.ok(result);
    }

    /**
    * POST /api/v1/solicitudes/{id}/asignar-transporte - Asigna un camión/transportista a la solicitud
    * Requiere rol OPERADOR o ADMIN
     * @param id ID de la solicitud
     * @param transportistaId ID del camión a asignar
     * @return Respuesta de la asignación
     */
    @PostMapping("/{id}/asignar-transporte")
    @PreAuthorize("hasAnyRole('OPERADOR','ADMIN')")
    public ResponseEntity<Object> assignTransport(@PathVariable Long id, @RequestParam Long transportistaId) {
        logger.info("POST /api/v1/solicitudes/{}/asignar-transporte - Asignando transporte - transportistaId: {}", id, transportistaId);
        Object result = solicitudService.assignTransport(id, transportistaId);
        logger.info("POST /api/v1/solicitudes/{}/asignar-transporte - Respuesta: 200 - Transporte asignado", id);
        return ResponseEntity.ok(result);
    }

    /**
     * POST /api/v1/solicitudes/{id}/rutas - Solicita rutas/precomputadas para una solicitud
     * Requiere rol CLIENTE, OPERADOR o ADMIN
     * @param id ID de la solicitud
     * @return Respuesta del microservicio de rutas (opciones o ruta creada)
     */
    @PostMapping("/{id}/rutas")
    @PreAuthorize("hasAnyRole('OPERADOR','ADMIN')")
    public ResponseEntity<Object> requestRoute(@PathVariable Long id) {
        logger.info("POST /api/v1/solicitudes/{}/rutas - Solicitando ruta para solicitud", id);
        try {
            Object resp = solicitudService.requestRoute(id);
            logger.info("POST /api/v1/solicitudes/{}/rutas - Respuesta del servicio de rutas obtenida", id);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            logger.error("POST /api/v1/solicitudes/{}/rutas - Error solicitando ruta: {}", id, e.getMessage());
            return ResponseEntity.status(500).body(java.util.Map.of("error", e.getMessage()));
        }
    }

    

    /**
    * PATCH /api/v1/solicitudes/{id}/estado - Actualiza el estado de una solicitud
    * Requiere rol OPERADOR o ADMIN
     * @param id ID de la solicitud
     * @param estadoId ID del nuevo estado
     * @return Solicitud con estado actualizado
     */
    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('OPERADOR','ADMIN')")
    public ResponseEntity<SolicitudDTO> updateEstado(@PathVariable Long id, @RequestParam Long estadoId) {
        logger.info("PATCH /api/v1/solicitudes/{}/estado - Actualizando estado - nuevoEstadoId: {}", id, estadoId);
        SolicitudDTO solicitudDTO = solicitudService.updateEstado(id, estadoId);
        logger.info("PATCH /api/v1/solicitudes/{}/estado - Respuesta: 200 - Estado actualizado", id);
        return ResponseEntity.ok(solicitudDTO);
    }


    /**
    * PATCH /api/v1/solicitudes/{id}/finalizar - Persiste el costo final y tiempo real de la solicitud
    * Requiere rol OPERADOR o ADMIN
     */
    @PatchMapping("/{id}/finalizar")
    @PreAuthorize("hasAnyRole('OPERADOR','ADMIN')")
    public ResponseEntity<SolicitudDTO> finalizar(@PathVariable Long id,
                                                  @RequestParam(required = false) java.math.BigDecimal costoFinal,
                                                  @RequestParam(required = false) java.math.BigDecimal tiempoReal) {
        logger.info("PATCH /api/v1/solicitudes/{}/finalizar - Persistiendo costoFinal: {}, tiempoReal: {}", id, costoFinal, tiempoReal);
        SolicitudDTO solicitudDTO = solicitudService.finalizar(id, costoFinal, tiempoReal);
        if (solicitudDTO == null) {
            logger.warn("PATCH /api/v1/solicitudes/{}/finalizar - Respuesta: 404 - Solicitud no encontrada", id);
            return ResponseEntity.notFound().build();
        }
        logger.info("PATCH /api/v1/solicitudes/{}/finalizar - Respuesta: 200 - Solicitud finalizada", id);
        return ResponseEntity.ok(solicitudDTO);
    }

    /**
     * PATCH /api/v1/solicitudes/{id}/ruta?rutaId=... - Asocia una ruta a la solicitud
     * Requiere rol OPERADOR o ADMIN
     */
    @PatchMapping("/{id}/ruta")
    @PreAuthorize("hasAnyRole('OPERADOR','ADMIN')")
    public ResponseEntity<SolicitudDTO> setRutaId(@PathVariable Long id, @RequestParam Long rutaId) {
        logger.info("PATCH /api/v1/solicitudes/{}/ruta - Asociando rutaId: {}", id, rutaId);
        try {
            SolicitudDTO dto = solicitudService.setRutaId(id, rutaId);
            return ResponseEntity.ok(dto);
        } catch (RuntimeException e) {
            logger.warn("PATCH /api/v1/solicitudes/{}/ruta - Error: {}", id, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * POST /api/v1/solicitudes/{solicitudId}/opciones/{opcionId}/confirmar
     * Confirma la opción seleccionada
     * Requiere rol OPERADOR o ADMIN
     */
    @PostMapping("/{solicitudId}/opciones/{opcionId}/confirmar")
    @PreAuthorize("hasAnyRole('OPERADOR','ADMIN')")
    public ResponseEntity<Object> confirmOpcion(
            @PathVariable Long solicitudId,
            @PathVariable Long opcionId) {
        logger.info("POST /api/v1/solicitudes/{}/opciones/{}/confirmar - Confirmando opción por opcionId", solicitudId, opcionId);
        try {
            java.util.Map<String, Object> resp = solicitudService.confirmRouteSelectionByOptionId(solicitudId, opcionId);
            // Al confirmar la opción queremos devolver también la solicitud actualizada
            // para que el cliente vea la asociación (rutaId) y el cambio de estado.
            com.backend.tpi.ms_solicitudes.dtos.SolicitudDTO solicitudActualizada = solicitudService.findById(solicitudId);
            java.util.Map<String, Object> combined = new java.util.HashMap<>();
            combined.put("ruta", resp);
            combined.put("solicitud", solicitudActualizada);
            logger.info("POST /api/v1/solicitudes/{}/opciones/{}/confirmar - Opción confirmada y solicitud actualizada devuelta", solicitudId, opcionId);
            return ResponseEntity.ok(combined);
        } catch (Exception e) {
            logger.error("Error confirmando opción para solicitud {} opción {}: {}", solicitudId, opcionId, e.getMessage());
            return ResponseEntity.status(500).body(java.util.Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /api/v1/solicitudes/{id}/estados-permitidos - Consulta transiciones de estado permitidas
     * Requiere rol OPERADOR o ADMIN
     * @param id ID de la solicitud
     * @return Lista de nombres de estados permitidos
     */
    @GetMapping("/{id}/estados-permitidos")
    @PreAuthorize("hasAnyRole('OPERADOR','ADMIN')")
    public ResponseEntity<List<String>> getEstadosPermitidos(@PathVariable Long id) {
        logger.info("GET /api/v1/solicitudes/{}/estados-permitidos - Consultando transiciones permitidas", id);
        List<String> estadosPermitidos = solicitudService.getEstadosPermitidos(id);
        logger.info("GET /api/v1/solicitudes/{}/estados-permitidos - Respuesta: 200 - {} estados permitidos", id, estadosPermitidos.size());
        return ResponseEntity.ok(estadosPermitidos);
    }

    /**
     * PUT /api/v1/solicitudes/{id}/estado - Cambia el estado de una solicitud
     * Requiere rol OPERADOR, TRANSPORTISTA o ADMIN
     * @param id ID de la solicitud
     * @param nuevoEstado Nombre del nuevo estado (PENDIENTE, PROGRAMADO, EN_RUTA, ENTREGADO, FINALIZADO)
     * @return Solicitud actualizada
     */
    @PutMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('OPERADOR','TRANSPORTISTA','ADMIN')")
    public ResponseEntity<?> cambiarEstado(
            @PathVariable Long id,
            @RequestParam String nuevoEstado) {
        logger.info("PUT /api/v1/solicitudes/{}/estado - Cambiando estado a: {}", id, nuevoEstado);
        try {
            SolicitudDTO solicitud = solicitudService.cambiarEstado(id, nuevoEstado);
            logger.info("PUT /api/v1/solicitudes/{}/estado - Respuesta: 200 - Estado cambiado", id);
            return ResponseEntity.ok(solicitud);
        } catch (RuntimeException e) {
            logger.error("PUT /api/v1/solicitudes/{}/estado - Respuesta: 404 - {}", id, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * GET /api/v1/solicitudes/contenedor/{contenedorId}/seguimiento - Obtiene el seguimiento completo de un contenedor
     * Busca la última solicitud asociada al contenedor y devuelve toda la información incluyendo tramos
     * Requiere rol CLIENTE, OPERADOR o ADMIN
     * @param contenedorId ID del contenedor a rastrear
     * @return Información completa de la última solicitud con tramos y estado del contenedor
     */
    @GetMapping("/contenedor/{contenedorId}/seguimiento")
    @PreAuthorize("hasAnyRole('CLIENTE','OPERADOR','ADMIN')")
    public ResponseEntity<?> getSeguimientoByContenedor(@PathVariable Long contenedorId) {
        logger.info("GET /api/v1/solicitudes/contenedor/{}/seguimiento - Buscando última solicitud del contenedor", contenedorId);
        try {
            java.util.Map<String, Object> seguimiento = solicitudService.getSeguimientoByContenedor(contenedorId);
            logger.info("GET /api/v1/solicitudes/contenedor/{}/seguimiento - Respuesta: 200 - Seguimiento encontrado", contenedorId);
            return ResponseEntity.ok(seguimiento);
        } catch (RuntimeException e) {
            logger.error("GET /api/v1/solicitudes/contenedor/{}/seguimiento - Error: {}", contenedorId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(java.util.Map.of("error", e.getMessage()));
        }
    }
}

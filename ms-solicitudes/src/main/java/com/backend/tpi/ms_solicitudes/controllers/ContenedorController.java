package com.backend.tpi.ms_solicitudes.controllers;

import com.backend.tpi.ms_solicitudes.dto.ContenedorDTO;
import com.backend.tpi.ms_solicitudes.dtos.SeguimientoContenedorDTO;
import com.backend.tpi.ms_solicitudes.models.Contenedor;
import com.backend.tpi.ms_solicitudes.models.EstadoContenedor;
import com.backend.tpi.ms_solicitudes.repositories.EstadoContenedorRepository;
import com.backend.tpi.ms_solicitudes.services.ContenedorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.backend.tpi.ms_solicitudes.services.ClienteService;
import com.backend.tpi.ms_solicitudes.models.Cliente;

import java.util.List;

/**
 * Controlador REST para gestionar Contenedores
 * Permite gestionar contenedores de carga y su seguimiento
 */
@RestController
@RequestMapping("/api/v1/contenedores")
@Tag(name = "Contenedores", description = "Gestión de contenedores")
public class ContenedorController {

    private static final Logger logger = LoggerFactory.getLogger(ContenedorController.class);

    @Autowired
    private ContenedorService contenedorService;

    @Autowired
    private ClienteService clienteService;

    @Autowired
    private EstadoContenedorRepository estadoContenedorRepository;

    /**
     * GET /api/v1/contenedores - Lista todos los contenedores del sistema con filtros opcionales
    * Requiere rol OPERADOR o ADMIN
     * @param estado Nombre del estado para filtrar (opcional)
     * @return Lista de contenedores (todos o filtrados por estado)
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('OPERADOR', 'ADMIN')")
    @Operation(summary = "Listar todos los contenedores con filtros opcionales")
    public ResponseEntity<List<Contenedor>> getAllContenedores(
            @RequestParam(required = false) String estado) {
        
        if (estado != null && !estado.isEmpty()) {
            logger.info("GET /api/v1/contenedores?estado={} - Listando contenedores por estado", estado);
            List<Contenedor> contenedores = contenedorService.findByEstadoNombre(estado);
            logger.info("GET /api/v1/contenedores?estado={} - Respuesta: 200 - {} contenedores encontrados", estado, contenedores.size());
            return ResponseEntity.ok(contenedores);
        }
        
        logger.info("GET /api/v1/contenedores - Listando todos los contenedores");
        List<Contenedor> contenedores = contenedorService.findAll();
        logger.info("GET /api/v1/contenedores - Respuesta: 200 - {} contenedores encontrados", contenedores.size());
        return ResponseEntity.ok(contenedores);
    }

    /**
     * GET /api/v1/contenedores/{id} - Obtiene un contenedor específico por ID
    * Requiere rol CLIENTE, OPERADOR o ADMIN
     * @param id ID del contenedor
     * @return Contenedor encontrado
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLIENTE', 'OPERADOR', 'ADMIN')")
    @Operation(summary = "Obtener contenedor por ID")
    public ResponseEntity<Contenedor> getContenedorById(@PathVariable Long id) {
        logger.info("GET /api/v1/contenedores/{} - Buscando contenedor por ID", id);
        Contenedor contenedor = contenedorService.findById(id);
        // Si el caller tiene SOLO rol CLIENTE (sin ADMIN ni OPERADOR), verificamos propiedad
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities() != null) {
            boolean hasCliente = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CLIENTE"));
            boolean hasAdminOrOperador = auth.getAuthorities().stream().anyMatch(a -> 
                a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_OPERADOR"));
            
            // Solo validar restricción si es CLIENTE puro (sin otros roles privilegiados)
            if (hasCliente && !hasAdminOrOperador) {
                if (auth instanceof JwtAuthenticationToken) {
                    Object emailObj = ((JwtAuthenticationToken) auth).getToken().getClaim("email");
                    String email = emailObj != null ? emailObj.toString() : null;
                    if (email != null) {
                        try {
                            Cliente c = clienteService.findByEmail(email);
                            if (contenedor.getClienteId() == null || !contenedor.getClienteId().equals(c.getId())) {
                                logger.warn("CLIENTE (email={}) intento acceder a contenedor ajeno: {}", email, id);
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

        logger.info("GET /api/v1/contenedores/{} - Respuesta: 200 - Contenedor encontrado", id);
        return ResponseEntity.ok(contenedor);
    }

    /**
    * GET /api/v1/contenedores/cliente/{clienteId} - Lista contenedores de un cliente
    * Requiere rol CLIENTE, OPERADOR o ADMIN
     * @param clienteId ID del cliente
     * @return Lista de contenedores del cliente
     */
    @GetMapping("/cliente/{clienteId}")
    @PreAuthorize("hasAnyRole('CLIENTE', 'OPERADOR', 'ADMIN')")
    @Operation(summary = "Listar contenedores por cliente")
    public ResponseEntity<List<Contenedor>> getContenedoresByCliente(@PathVariable Long clienteId) {
        logger.info("GET /api/v1/contenedores/cliente/{} - Buscando contenedores del cliente", clienteId);
        // Si el caller tiene SOLO rol CLIENTE (sin ADMIN ni OPERADOR), solo puede pedir sus propios contenedores
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities() != null) {
            boolean hasCliente = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CLIENTE"));
            boolean hasAdminOrOperador = auth.getAuthorities().stream().anyMatch(a -> 
                a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_OPERADOR"));
            
            // Solo validar restricción si es CLIENTE puro (sin otros roles privilegiados)
            if (hasCliente && !hasAdminOrOperador) {
                if (auth instanceof JwtAuthenticationToken) {
                    Object emailObj = ((JwtAuthenticationToken) auth).getToken().getClaim("email");
                    String email = emailObj != null ? emailObj.toString() : null;
                    if (email != null) {
                        try {
                            Cliente c = clienteService.findByEmail(email);
                            if (!c.getId().equals(clienteId)) {
                                logger.warn("CLIENTE (email={}) intento listar contenedores de otro cliente: {}", email, clienteId);
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

        List<Contenedor> contenedores = contenedorService.findByClienteId(clienteId);
        logger.info("GET /api/v1/contenedores/cliente/{} - Respuesta: 200 - {} contenedores encontrados", clienteId, contenedores.size());
        return ResponseEntity.ok(contenedores);
    }

    /**
    * POST /api/v1/contenedores - Crea un nuevo contenedor
    * Requiere rol CLIENTE, OPERADOR o ADMIN
     * @param dto Datos del contenedor a crear (estado como String)
     * @return Contenedor creado con código 201
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('CLIENTE', 'OPERADOR', 'ADMIN')")
    @Operation(summary = "Crear nuevo contenedor")
    public ResponseEntity<Contenedor> createContenedor(@RequestBody ContenedorDTO dto) {
        logger.info("POST /api/v1/contenedores - Creando nuevo contenedor");
        Contenedor contenedor = convertDtoToEntity(dto);
        Contenedor nuevoContenedor = contenedorService.save(contenedor);
        logger.info("POST /api/v1/contenedores - Respuesta: 201 - Contenedor creado con ID: {}", nuevoContenedor.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(nuevoContenedor);
    }

    /**
    * PUT /api/v1/contenedores/{id} - Actualiza un contenedor existente
    * Requiere rol OPERADOR o ADMIN
     * @param id ID del contenedor a actualizar
     * @param dto Datos actualizados del contenedor (estado como String)
     * @return Contenedor actualizado
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERADOR', 'ADMIN')")
    @Operation(summary = "Actualizar contenedor existente")
    public ResponseEntity<Contenedor> updateContenedor(@PathVariable Long id, @RequestBody ContenedorDTO dto) {
        logger.info("PUT /api/v1/contenedores/{} - Actualizando contenedor", id);
        Contenedor contenedor = convertDtoToEntity(dto);
        Contenedor contenedorActualizado = contenedorService.update(id, contenedor);
        logger.info("PUT /api/v1/contenedores/{} - Respuesta: 200 - Contenedor actualizado", id);
        return ResponseEntity.ok(contenedorActualizado);
    }

    /**
     * DELETE /api/v1/contenedores/{id} - Elimina un contenedor
     * Requiere rol ADMIN
     * @param id ID del contenedor a eliminar
     * @return No Content (204)
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Eliminar contenedor")
    public ResponseEntity<Void> deleteContenedor(@PathVariable Long id) {
        logger.info("DELETE /api/v1/contenedores/{} - Eliminando contenedor", id);
        contenedorService.deleteById(id);
        logger.info("DELETE /api/v1/contenedores/{} - Respuesta: 204 - Contenedor eliminado", id);
        return ResponseEntity.noContent().build();
    }

    /**
    * PATCH /api/v1/contenedores/{id} - Actualiza el estado de un contenedor
    * Requiere rol OPERADOR o ADMIN
     * @param id ID del contenedor
     * @param estadoId ID del nuevo estado (opcional si se usa estadoNombre)
     * @param estadoNombre Nombre del nuevo estado (opcional si se usa estadoId)
     * @return Contenedor con estado actualizado
     */
    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERADOR', 'ADMIN')")
    @Operation(summary = "Actualizar estado del contenedor")
    public ResponseEntity<Contenedor> updateEstadoContenedor(
            @PathVariable Long id, 
            @RequestParam(required = false) Long estadoId,
            @RequestParam(required = false) String estadoNombre) {
        
        Long estadoIdFinal = estadoId;
        
        // Si se proporciona nombre en lugar de ID, buscar el estado
        if (estadoNombre != null && !estadoNombre.isEmpty()) {
            EstadoContenedor estado = estadoContenedorRepository.findByNombre(estadoNombre)
                .orElseThrow(() -> new RuntimeException("Estado no encontrado: " + estadoNombre));
            estadoIdFinal = estado.getId();
            logger.info("PATCH /api/v1/contenedores/{} - Actualizando estado - estadoNombre: {} -> estadoId: {}", id, estadoNombre, estadoIdFinal);
        } else if (estadoId != null) {
            logger.info("PATCH /api/v1/contenedores/{} - Actualizando estado - estadoId: {}", id, estadoId);
        } else {
            throw new RuntimeException("Debe proporcionar estadoId o estadoNombre");
        }
        
        Contenedor contenedor = contenedorService.updateEstado(id, estadoIdFinal);
        logger.info("PATCH /api/v1/contenedores/{} - Respuesta: 200 - Estado actualizado", id);
        return ResponseEntity.ok(contenedor);
    }

    /**
    * GET /api/v1/contenedores/{id}/seguimiento - Consulta la ubicación y estado actual de un contenedor
    * Requiere rol CLIENTE, OPERADOR o ADMIN
     * @param id ID del contenedor
     * @return Información de seguimiento del contenedor
     */
    @GetMapping("/{id}/seguimiento")
    @PreAuthorize("hasAnyRole('CLIENTE', 'OPERADOR', 'ADMIN')")
    @Operation(summary = "Consultar seguimiento del contenedor")
    public ResponseEntity<SeguimientoContenedorDTO> getSeguimiento(@PathVariable Long id) {
        logger.info("GET /api/v1/contenedores/{}/seguimiento - Consultando seguimiento", id);
        SeguimientoContenedorDTO seguimiento = contenedorService.getSeguimiento(id);
        logger.info("GET /api/v1/contenedores/{}/seguimiento - Respuesta: 200 - Seguimiento obtenido", id);
        return ResponseEntity.ok(seguimiento);
    }

    /**
    * GET /api/v1/contenedores/{id}/estados-permitidos - Consulta los estados a los que puede transicionar el contenedor
    * Requiere rol OPERADOR o ADMIN
     * @param id ID del contenedor
     * @return Lista de nombres de estados permitidos
     */
    @GetMapping("/{id}/estados-permitidos")
    @PreAuthorize("hasAnyRole('OPERADOR', 'ADMIN')")
    @Operation(summary = "Consultar estados permitidos para el contenedor")
    public ResponseEntity<List<String>> getEstadosPermitidos(@PathVariable Long id) {
        logger.info("GET /api/v1/contenedores/{}/estados-permitidos - Consultando transiciones permitidas", id);
        List<String> estadosPermitidos = contenedorService.getEstadosPermitidos(id);
        logger.info("GET /api/v1/contenedores/{}/estados-permitidos - Respuesta: 200 - {} estados permitidos", id, estadosPermitidos.size());
        return ResponseEntity.ok(estadosPermitidos);
    }

    /**
     * Método auxiliar para convertir ContenedorDTO a Contenedor entity
     * Busca el EstadoContenedor por nombre y lo asigna al contenedor
     */
    private Contenedor convertDtoToEntity(ContenedorDTO dto) {
        Contenedor contenedor = new Contenedor();
        contenedor.setId(dto.getId());
        contenedor.setPeso(dto.getPeso());
        contenedor.setVolumen(dto.getVolumen());
        contenedor.setClienteId(dto.getClienteId());
        
        // Si se proporciona un nombre de estado, buscarlo y asignarlo
        if (dto.getEstado() != null && !dto.getEstado().isEmpty()) {
            EstadoContenedor estado = estadoContenedorRepository.findByNombre(dto.getEstado())
                .orElseThrow(() -> new RuntimeException("Estado no encontrado: " + dto.getEstado()));
            contenedor.setEstado(estado);
        }
        
        return contenedor;
    }
}

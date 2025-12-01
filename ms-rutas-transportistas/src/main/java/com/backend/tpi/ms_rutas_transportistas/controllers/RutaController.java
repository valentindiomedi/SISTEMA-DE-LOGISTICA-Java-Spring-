package com.backend.tpi.ms_rutas_transportistas.controllers;

import com.backend.tpi.ms_rutas_transportistas.dtos.*;
import com.backend.tpi.ms_rutas_transportistas.services.RutaService;
import com.backend.tpi.ms_rutas_transportistas.services.RutaTentativaService;
import com.backend.tpi.ms_rutas_transportistas.services.TramoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Controlador REST para gestionar Rutas
 * Maneja la creación de rutas, asignación de transportistas y gestión de tramos
 */
@RestController
@RequestMapping("/api/v1/rutas")
@Tag(name = "Rutas", description = "Gestión de rutas y tramos de transporte")
public class RutaController {

    private static final Logger logger = LoggerFactory.getLogger(RutaController.class);

    @Autowired
    private RutaService rutaService;

    @Autowired
    private TramoService tramoService;
    
    @Autowired
    private RutaTentativaService rutaTentativaService;
    
    @Autowired
    private com.backend.tpi.ms_rutas_transportistas.services.RutaOpcionService rutaOpcionService;

    @Autowired
    private com.backend.tpi.ms_rutas_transportistas.repositories.RutaOpcionRepository rutaOpcionRepository;

    @Autowired
    private com.backend.tpi.ms_rutas_transportistas.repositories.RutaRepository rutaRepository;

    /**
     * Crea una nueva ruta para una solicitud de transporte
     * @param createRutaDTO Datos de la ruta a crear
     * @return Ruta creada
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('OPERADOR','ADMIN')")
    public ResponseEntity<Object> create(@RequestBody CreateRutaDTO createRutaDTO) {
        logger.info("POST /api/v1/rutas - Creando nueva ruta para solicitud ID: {}", createRutaDTO.getIdSolicitud());
        try {
            // Validar si ya existe una ruta para esta solicitud
            Optional<com.backend.tpi.ms_rutas_transportistas.models.Ruta> rutaExistente = 
                    rutaRepository.findByIdSolicitud(createRutaDTO.getIdSolicitud());
            if (rutaExistente.isPresent()) {
                logger.warn("POST /api/v1/rutas - La solicitud {} ya tiene una ruta asignada (ID: {})", 
                        createRutaDTO.getIdSolicitud(), rutaExistente.get().getId());
                return ResponseEntity.status(409)
                        .body(java.util.Map.of("mensaje", "La solicitud ya tiene una ruta asignada", 
                                               "rutaId", rutaExistente.get().getId()));
            }
            
            RutaDTO result = rutaService.create(createRutaDTO);
            logger.info("POST /api/v1/rutas - Respuesta: 200 - Ruta creada con ID: {}", result.getId());
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            logger.warn("POST /api/v1/rutas - Validación: {}", e.getMessage());
            return ResponseEntity.badRequest().body(java.util.Map.of("mensaje", e.getMessage()));
        } catch (Exception e) {
            logger.error("POST /api/v1/rutas - Error: {}", e.getMessage());
            return ResponseEntity.status(500).body(java.util.Map.of("mensaje", "Error interno del servidor"));
        }
    }

    /**
     * Genera y persiste opciones (tentativas) para una solicitud sin crear una Ruta definitiva
     * POST /api/v1/solicitudes/{solicitudId}/opciones
     */
    @PostMapping("/solicitudes/{solicitudId}/opciones")
    @PreAuthorize("hasAnyRole('OPERADOR','ADMIN')")
    public ResponseEntity<?> createOptionsForSolicitud(@PathVariable Long solicitudId) {
        logger.info("POST /api/v1/solicitudes/{}/opciones - Generando y persistiendo opciones para solicitud", solicitudId);
        try {
            java.util.List<com.backend.tpi.ms_rutas_transportistas.dtos.RutaTentativaDTO> variantes = rutaService.generateOptionsForSolicitud(solicitudId);
            java.util.List<com.backend.tpi.ms_rutas_transportistas.models.RutaOpcion> saved = rutaOpcionService.saveOptionsForSolicitud(solicitudId, variantes);
            return ResponseEntity.ok(saved);
        } catch (IllegalStateException e) {
            logger.error("Error de estado al generar opciones para solicitud {}: {}", solicitudId, e.getMessage());
            return ResponseEntity.status(400).body(java.util.Map.of(
                "error", "Error de validación",
                "mensaje", e.getMessage()
            ));
        } catch (IllegalArgumentException e) {
            logger.error("Error de argumento al generar opciones para solicitud {}: {}", solicitudId, e.getMessage());
            return ResponseEntity.status(400).body(java.util.Map.of(
                "error", "Datos inválidos",
                "mensaje", e.getMessage()
            ));
        } catch (Exception e) {
            logger.error("Error generando opciones para solicitud {}: {}", solicitudId, e.getMessage(), e);
            return ResponseEntity.status(500).body(java.util.Map.of(
                "error", "Error interno del servidor",
                "mensaje", "Ocurrió un error inesperado al generar las opciones de ruta"
            ));
        }
    }

    /**
     * Lista opciones persistidas para una solicitud
     * GET /api/v1/solicitudes/{solicitudId}/opciones
     */
    @GetMapping("/solicitudes/{solicitudId}/opciones")
    @PreAuthorize("hasAnyRole('OPERADOR','ADMIN','TRANSPORTISTA')")
    public ResponseEntity<java.util.List<com.backend.tpi.ms_rutas_transportistas.dtos.RutaOpcionDTO>> listOptionsForSolicitud(@PathVariable Long solicitudId) {
        logger.info("GET /api/v1/solicitudes/{}/opciones - Listando opciones persistidas", solicitudId);
        try {
            java.util.List<com.backend.tpi.ms_rutas_transportistas.models.RutaOpcion> opciones = rutaOpcionService.listOptionsForSolicitud(solicitudId);
            java.util.List<com.backend.tpi.ms_rutas_transportistas.dtos.RutaOpcionDTO> dtos = new java.util.ArrayList<>();
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            for (com.backend.tpi.ms_rutas_transportistas.models.RutaOpcion opcion : opciones) {
                try {
                    com.backend.tpi.ms_rutas_transportistas.dtos.RutaOpcionDTO dto = mapRutaOpcionToDTO(opcion, mapper);
                    dtos.add(dto);
                } catch (Exception ex) {
                    logger.warn("No se pudo deserializar opcion id {}: {}", opcion.getId(), ex.getMessage());
                    // En caso de error de parseo añadimos una representación mínima
                    dtos.add(com.backend.tpi.ms_rutas_transportistas.dtos.RutaOpcionDTO.builder()
                            .id(opcion.getId())
                            .rutaId(opcion.getRutaId())
                            .solicitudId(opcion.getSolicitudId())
                            .opcionIndex(opcion.getOpcionIndex())
                            .distanciaTotal(opcion.getDistanciaTotal())
                            .duracionTotalHoras(opcion.getDuracionTotalHoras())
                            .costoTotal(opcion.getCostoTotal())
                            .fechaCreacion(opcion.getFechaCreacion())
                            .build());
                }
            }
            return ResponseEntity.ok(dtos);
        } catch (Exception e) {
            logger.error("Error listando opciones para solicitud {}: {}", solicitudId, e.getMessage(), e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Confirma una opción persistida para una solicitud: crea la Ruta definitiva y borra/archiva las otras opciones
     * POST /api/v1/rutas/opciones/{opcionId}/confirmar
     */
    @PostMapping("/opciones/{opcionId}/confirmar")
    @PreAuthorize("hasAnyRole('OPERADOR','ADMIN')")
    public ResponseEntity<RutaDTO> confirmarOpcionPersistida(@PathVariable Long opcionId) {
        logger.info("POST /api/v1/rutas/opciones/{}/confirmar - Confirmando opción", opcionId);
        try {
            com.backend.tpi.ms_rutas_transportistas.models.RutaOpcion opcion = rutaOpcionService.findById(opcionId);
            if (opcion == null) return ResponseEntity.notFound().build();
            Long solicitudId = opcion.getSolicitudId();
            if (solicitudId == null) {
                return ResponseEntity.badRequest().build();
            }

            logger.info("=== CONFIRMACION: Opcion {} para solicitud {} ===", opcionId, solicitudId);
            logger.info("TramosJson de DB (primeros 500 chars): {}", 
                opcion.getTramosJson() != null && opcion.getTramosJson().length() > 500 
                    ? opcion.getTramosJson().substring(0, 500) + "..." 
                    : opcion.getTramosJson());

            // Convertir RutaOpcion a RutaTentativaDTO
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            
            // Deserializar los arrays JSON
            List<Long> depositosIds = mapper.readValue(opcion.getDepositosIdsJson(), 
                    new com.fasterxml.jackson.core.type.TypeReference<List<Long>>() {});
            List<String> depositosNombres = mapper.readValue(opcion.getDepositosNombresJson(), 
                    new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
            List<com.backend.tpi.ms_rutas_transportistas.dtos.TramoTentativoDTO> tramos = mapper.readValue(opcion.getTramosJson(), 
                    new com.fasterxml.jackson.core.type.TypeReference<List<com.backend.tpi.ms_rutas_transportistas.dtos.TramoTentativoDTO>>() {});
            
            logger.info("Tramos deserializados: {}", tramos != null ? tramos.size() : 0);
            if (tramos != null) {
                for (int i = 0; i < tramos.size(); i++) {
                    com.backend.tpi.ms_rutas_transportistas.dtos.TramoTentativoDTO t = tramos.get(i);
                    logger.info("  Tramo {}: orden={}, origenDepId={}, destinoDepId={}, dist={}", 
                        i+1, t.getOrden(), t.getOrigenDepositoId(), t.getDestinoDepositoId(), t.getDistanciaKm());
                }
            }
            
            // Reconstruir RutaTentativaDTO
            com.backend.tpi.ms_rutas_transportistas.dtos.RutaTentativaDTO rutaTentativa = 
                    com.backend.tpi.ms_rutas_transportistas.dtos.RutaTentativaDTO.builder()
                    .depositosIds(depositosIds)
                    .depositosNombres(depositosNombres)
                    .distanciaTotal(opcion.getDistanciaTotal())
                    .duracionTotalHoras(opcion.getDuracionTotalHoras())
                    .numeroTramos(tramos.size())
                    .tramos(tramos)
                    .geometry(opcion.getGeometry())
                    .exitoso(true)
                    .build();

            // Crear Ruta definitiva usando RutaService
            RutaDTO rutaDto = rutaService.createFromTentativa(solicitudId, rutaTentativa);

            // Borrar las opciones relacionadas con esta solicitud
            rutaOpcionService.deleteBySolicitudId(solicitudId);

            return ResponseEntity.ok(rutaDto);
        } catch (IllegalArgumentException e) {
            logger.warn("Confirmación inválida: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Error confirmando opción: {}", e.getMessage(), e);
            return ResponseEntity.status(500).build();
        }
    }

    // Removed deprecated POST /api/v1/rutas/{id}/opciones per deprecation policy

    /**
     * GET /api/v1/rutas/{id}/opciones - Lista las opciones persistidas para una ruta
     */
    @GetMapping("/{id}/opciones")
    @PreAuthorize("hasAnyRole('OPERADOR','ADMIN','TRANSPORTISTA')")
    public ResponseEntity<java.util.List<com.backend.tpi.ms_rutas_transportistas.dtos.RutaOpcionDTO>> listOptions(@PathVariable Long id) {
        logger.info("GET /api/v1/rutas/{}/opciones - Listando opciones guardadas", id);
        try {
            java.util.List<com.backend.tpi.ms_rutas_transportistas.models.RutaOpcion> opciones = rutaOpcionService.listOptionsForRuta(id);
            java.util.List<com.backend.tpi.ms_rutas_transportistas.dtos.RutaOpcionDTO> dtos = new java.util.ArrayList<>();
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            for (com.backend.tpi.ms_rutas_transportistas.models.RutaOpcion opcion : opciones) {
                try {
                    dtos.add(mapRutaOpcionToDTO(opcion, mapper));
                } catch (Exception ex) {
                    logger.warn("No se pudo deserializar opcion id {}: {}", opcion.getId(), ex.getMessage());
                    dtos.add(com.backend.tpi.ms_rutas_transportistas.dtos.RutaOpcionDTO.builder()
                            .id(opcion.getId())
                            .rutaId(opcion.getRutaId())
                            .solicitudId(opcion.getSolicitudId())
                            .opcionIndex(opcion.getOpcionIndex())
                            .distanciaTotal(opcion.getDistanciaTotal())
                            .duracionTotalHoras(opcion.getDuracionTotalHoras())
                            .costoTotal(opcion.getCostoTotal())
                            .fechaCreacion(opcion.getFechaCreacion())
                            .build());
                }
            }
            return ResponseEntity.ok(dtos);
        } catch (Exception e) {
            logger.error("Error listando opciones para ruta {}: {}", id, e.getMessage());
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Helper: convierte una entidad RutaOpcion en el DTO legible (sin geometry)
     */
    private com.backend.tpi.ms_rutas_transportistas.dtos.RutaOpcionDTO mapRutaOpcionToDTO(
            com.backend.tpi.ms_rutas_transportistas.models.RutaOpcion opcion,
            com.fasterxml.jackson.databind.ObjectMapper mapper) throws Exception {

        java.util.List<java.lang.Long> depositosIds = null;
        java.util.List<java.lang.String> depositosNombres = null;
        java.util.List<com.backend.tpi.ms_rutas_transportistas.dtos.TramoTentativoDTO> tramos = null;

        if (opcion.getDepositosIdsJson() != null && !opcion.getDepositosIdsJson().isBlank()) {
            depositosIds = mapper.readValue(opcion.getDepositosIdsJson(),
                    new com.fasterxml.jackson.core.type.TypeReference<java.util.List<java.lang.Long>>() {});
        }
        if (opcion.getDepositosNombresJson() != null && !opcion.getDepositosNombresJson().isBlank()) {
            depositosNombres = mapper.readValue(opcion.getDepositosNombresJson(),
                    new com.fasterxml.jackson.core.type.TypeReference<java.util.List<java.lang.String>>() {});
        }
        if (opcion.getTramosJson() != null && !opcion.getTramosJson().isBlank()) {
            tramos = mapper.readValue(opcion.getTramosJson(),
                new com.fasterxml.jackson.core.type.TypeReference<java.util.List<com.backend.tpi.ms_rutas_transportistas.dtos.TramoTentativoDTO>>() {});
        }

        // Construir resumen legible
        String resumen = String.format("Opción %s: %.2f km, %.2f h, %d tramos",
            opcion.getOpcionIndex() != null ? opcion.getOpcionIndex() : 0,
            opcion.getDistanciaTotal() != null ? opcion.getDistanciaTotal() : 0.0,
            opcion.getDuracionTotalHoras() != null ? opcion.getDuracionTotalHoras() : 0.0,
            tramos != null ? tramos.size() : 0);

        java.util.List<String> resumenTramos = new java.util.ArrayList<>();
        if (tramos != null) {
            for (com.backend.tpi.ms_rutas_transportistas.dtos.TramoTentativoDTO t : tramos) {
            String origen = t.getOrigenDepositoNombre() != null ? t.getOrigenDepositoNombre() : "Origen";
            String destino = t.getDestinoDepositoNombre() != null ? t.getDestinoDepositoNombre() : "Destino";
            Double dist = t.getDistanciaKm() != null ? t.getDistanciaKm() : 0.0;
            Double dur = t.getDuracionHoras() != null ? t.getDuracionHoras() : 0.0;
            resumenTramos.add(String.format("Tramo %d: %s -> %s (%.2f km, %.2f h)",
                t.getOrden() != null ? t.getOrden() : 0,
                origen, destino, dist, dur));
            }
        }

        return com.backend.tpi.ms_rutas_transportistas.dtos.RutaOpcionDTO.builder()
            .id(opcion.getId())
            .rutaId(opcion.getRutaId())
            .solicitudId(opcion.getSolicitudId())
            .opcionIndex(opcion.getOpcionIndex())
            .distanciaTotal(opcion.getDistanciaTotal())
            .duracionTotalHoras(opcion.getDuracionTotalHoras())
            .costoTotal(opcion.getCostoTotal())
            .depositosIds(depositosIds)
            .depositosNombres(depositosNombres)
            .tramos(tramos)
            .resumen(resumen)
            .resumenTramos(resumenTramos)
            .fechaCreacion(opcion.getFechaCreacion())
            .build();
    }

    /**
     * POST /api/v1/rutas/{id}/opciones/{opcionId}/seleccionar
     * Selecciona una opción y reemplaza los tramos de una ruta existente.
     * Después de seleccionar, elimina todas las opciones restantes de la solicitud.
     * Comportamiento similar a /confirmar pero aplicado a una ruta ya existente.
     */
    @PostMapping("/{id}/opciones/{opcionId}/seleccionar")
    @PreAuthorize("hasAnyRole('OPERADOR','ADMIN')")
    public ResponseEntity<RutaDTO> selectOption(@PathVariable Long id, @PathVariable Long opcionId) {
        logger.info("POST /api/v1/rutas/{}/opciones/{}/seleccionar - Seleccionando opción", id, opcionId);
        try {
            com.backend.tpi.ms_rutas_transportistas.models.Ruta ruta = rutaOpcionService.selectOption(id, opcionId);
            RutaDTO dto = rutaService.findById(ruta.getId());
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException e) {
            logger.warn("Selección inválida: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Error al seleccionar opción: {}", e.getMessage());
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Obtiene la lista de todas las rutas registradas
     * @return Lista de rutas
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('CLIENTE','OPERADOR','ADMIN','TRANSPORTISTA')")
    public ResponseEntity<List<RutaDTO>> findAll() {
        logger.info("GET /api/v1/rutas - Listando todas las rutas");
        List<RutaDTO> result = rutaService.findAll();
        logger.info("GET /api/v1/rutas - Respuesta: 200 - {} rutas encontradas", result.size());
        return ResponseEntity.ok(result);
    }

    /**
     * Obtiene una ruta específica por su ID
     * @param id ID de la ruta
     * @return Ruta encontrada o 404 si no existe
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLIENTE','OPERADOR','ADMIN','TRANSPORTISTA')")
    public ResponseEntity<RutaDTO> findById(@PathVariable Long id) {
        logger.info("GET /api/v1/rutas/{} - Buscando ruta por ID", id);
        RutaDTO rutaDTO = rutaService.findById(id);
        if (rutaDTO == null) {
            logger.warn("GET /api/v1/rutas/{} - Respuesta: 404 - Ruta no encontrada", id);
            return ResponseEntity.notFound().build();
        }
        logger.info("GET /api/v1/rutas/{} - Respuesta: 200 - Ruta encontrada", id);
        return ResponseEntity.ok(rutaDTO);
    }

    /**
     * Elimina una ruta del sistema
     * @param id ID de la ruta a eliminar
     * @return Respuesta sin contenido
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERADOR','ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        logger.info("DELETE /api/v1/rutas/{} - Eliminando ruta", id);
        rutaService.delete(id);
        logger.info("DELETE /api/v1/rutas/{} - Respuesta: 204 - Ruta eliminada", id);
        return ResponseEntity.noContent().build();
    }

    // ---- Integration endpoints (delegan al service) ----

    /**
     * Busca la ruta asociada a una solicitud específica
     * @param solicitudId ID de la solicitud
     * @return Ruta asociada a la solicitud
     */
    @GetMapping("/por-solicitud/{solicitudId}")
    @PreAuthorize("hasAnyRole('OPERADOR','ADMIN','TRANSPORTISTA')")
    public ResponseEntity<Object> findBySolicitud(@PathVariable Long solicitudId) {
        logger.info("GET /api/v1/rutas/por-solicitud/{} - Buscando ruta por solicitud", solicitudId);
        Object result = rutaService.findBySolicitudId(solicitudId);
        logger.info("GET /api/v1/rutas/por-solicitud/{} - Respuesta: 200 - Ruta encontrada", solicitudId);
        return ResponseEntity.ok(result);
    }

    /**
     * Obtiene todos los tramos de una ruta específica
     * @param id ID de la ruta
     * @return Lista de tramos de la ruta
     */
    @GetMapping("/{id}/tramos")
    @PreAuthorize("hasAnyRole('OPERADOR','TRANSPORTISTA','ADMIN','CLIENTE')")
    public ResponseEntity<List<TramoDTO>> getTramosDeRuta(@PathVariable Long id) {
        logger.info("GET /api/v1/rutas/{}/tramos - Buscando tramos de la ruta", id);
        List<TramoDTO> tramos = tramoService.findByRutaId(id);
        logger.info("GET /api/v1/rutas/{}/tramos - Respuesta: 200 - {} tramos encontrados", id, tramos.size());
        return ResponseEntity.ok(tramos);
    }

    /**
     * Marca el inicio de un tramo de transporte
     * @param id ID de la ruta
     * @param tramoId ID del tramo a iniciar
     * @return Tramo iniciado
     */
    @PostMapping("/{id}/tramos/{tramoId}/iniciar")
    @PreAuthorize("hasAnyRole('TRANSPORTISTA','OPERADOR','ADMIN')")
    @Operation(summary = "Marcar el inicio de un tramo de transporte")
    public ResponseEntity<TramoDTO> iniciarTramo(
            @PathVariable Long id,
            @PathVariable Long tramoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fechaHoraReal) {
        logger.info("POST /api/v1/rutas/{}/tramos/{}/iniciar - Iniciando tramo", id, tramoId);
        TramoDTO tramo = tramoService.iniciarTramo(id, tramoId, fechaHoraReal);
        if (tramo == null) {
            logger.warn("POST /api/v1/rutas/{}/tramos/{}/iniciar - Respuesta: 404 - Tramo no encontrado", id, tramoId);
            return ResponseEntity.notFound().build();
        }
        logger.info("POST /api/v1/rutas/{}/tramos/{}/iniciar - Respuesta: 200 - Tramo iniciado", id, tramoId);
        return ResponseEntity.ok(tramo);
    }

    /**
     * Marca la finalización de un tramo de transporte
     * @param id ID de la ruta
     * @param tramoId ID del tramo a finalizar
     * @param fechaHoraReal Fecha y hora de finalización (opcional, usa fecha actual si no se proporciona)
     * @return Tramo finalizado
     */
    @PostMapping("/{id}/tramos/{tramoId}/finalizar")
    @PreAuthorize("hasAnyRole('TRANSPORTISTA','OPERADOR','ADMIN')")
    @Operation(summary = "Marcar la finalización de un tramo de transporte")
    public ResponseEntity<TramoDTO> finalizarTramo(
            @PathVariable Long id,
            @PathVariable Long tramoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fechaHoraReal) {
        logger.info("POST /api/v1/rutas/{}/tramos/{}/finalizar - Finalizando tramo", id, tramoId);
        
        TramoDTO tramo = tramoService.finalizarTramo(id, tramoId, fechaHoraReal);
        if (tramo == null) {
            logger.warn("POST /api/v1/rutas/{}/tramos/{}/finalizar - Respuesta: 404 - Tramo no encontrado", id, tramoId);
            return ResponseEntity.notFound().build();
        }
        logger.info("POST /api/v1/rutas/{}/tramos/{}/finalizar - Respuesta: 200 - Tramo finalizado", id, tramoId);
        return ResponseEntity.ok(tramo);
    }

    /**
     * Calcula las distancias y duraciones de todos los tramos de una ruta usando OSRM
     * @param id ID de la ruta
     * @return Resultado del cálculo con distancias y duraciones actualizadas
     */
    @PostMapping("/{id}/calcular-distancias")
    @PreAuthorize("hasAnyRole('OPERADOR','ADMIN')")
    @Operation(summary = "Calcular distancias y duraciones de todos los tramos",
            description = "Usa OSRM para calcular las distancias y duraciones reales de cada tramo de la ruta")
    public ResponseEntity<java.util.Map<String, Object>> calcularDistancias(@PathVariable Long id) {
        logger.info("POST /api/v1/rutas/{}/calcular-distancias - Calculando distancias de tramos", id);
        try {
            java.util.Map<String, Object> resultado = rutaService.calcularRutaCompleta(id);
            logger.info("POST /api/v1/rutas/{}/calcular-distancias - Respuesta: 200 - {} tramos actualizados", 
                id, resultado.get("tramosActualizados"));
            return ResponseEntity.ok(resultado);
        } catch (IllegalArgumentException e) {
            logger.warn("POST /api/v1/rutas/{}/calcular-distancias - Respuesta: 400 - {}", id, e.getMessage());
            java.util.Map<String, Object> error = new java.util.HashMap<>();
            error.put("exitoso", false);
            error.put("mensaje", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            logger.error("POST /api/v1/rutas/{}/calcular-distancias - Respuesta: 500 - {}", id, e.getMessage());
            java.util.Map<String, Object> error = new java.util.HashMap<>();
            error.put("exitoso", false);
            error.put("mensaje", "Error al calcular distancias: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * Calcula el costo total de una ruta basado en las distancias de sus tramos
     * @param id ID de la ruta
     * @return Resultado del cálculo con el costo total y costos por tramo
     */
    @PostMapping("/{id}/calcular-costos")
    @PreAuthorize("hasAnyRole('OPERADOR','ADMIN')")
    @Operation(summary = "Calcular costos de todos los tramos de la ruta",
            description = "Calcula el costo de cada tramo basado en su distancia y la tarifa por km configurada")
    public ResponseEntity<java.util.Map<String, Object>> calcularCostos(@PathVariable Long id) {
        logger.info("POST /api/v1/rutas/{}/calcular-costos - Calculando costos", id);
        try {
            java.util.Map<String, Object> resultado = rutaService.calcularCostoRuta(id);
            logger.info("POST /api/v1/rutas/{}/calcular-costos - Respuesta: 200 - Costo total: ${}", 
                id, resultado.get("costoTotal"));
            return ResponseEntity.ok(resultado);
        } catch (IllegalArgumentException e) {
            logger.warn("POST /api/v1/rutas/{}/calcular-costos - Respuesta: 400 - {}", id, e.getMessage());
            java.util.Map<String, Object> error = new java.util.HashMap<>();
            error.put("exitoso", false);
            error.put("mensaje", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            logger.error("POST /api/v1/rutas/{}/calcular-costos - Respuesta: 500 - {}", id, e.getMessage());
            java.util.Map<String, Object> error = new java.util.HashMap<>();
            error.put("exitoso", false);
            error.put("mensaje", "Error al calcular costos: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * Calcula tanto las distancias como los costos de una ruta en una sola operación
     * @param id ID de la ruta
     * @return Resultado combinado con distancias, duraciones y costos
     */
    @PostMapping("/{id}/calcular-completo")
    @PreAuthorize("hasAnyRole('OPERADOR','ADMIN')")
    @Operation(summary = "Calcular distancias y costos completos de la ruta",
            description = "Calcula las distancias de todos los tramos usando OSRM y luego calcula los costos basados en esas distancias")
    public ResponseEntity<java.util.Map<String, Object>> calcularCompleto(@PathVariable Long id) {
        logger.info("POST /api/v1/rutas/{}/calcular-completo - Calculando distancias y costos", id);
        try {
            // Primero calcular distancias
            java.util.Map<String, Object> distancias = rutaService.calcularRutaCompleta(id);
            
            // Luego calcular costos
            java.util.Map<String, Object> costos = rutaService.calcularCostoRuta(id);
            
            // Combinar resultados
            java.util.Map<String, Object> resultado = new java.util.HashMap<>();
            resultado.put("rutaId", id);
            resultado.put("distanciaTotal", distancias.get("distanciaTotal"));
            resultado.put("duracionTotalHoras", distancias.get("duracionTotalHoras"));
            resultado.put("duracionTotalMinutos", distancias.get("duracionTotalMinutos"));
            resultado.put("numeroTramos", distancias.get("numeroTramos"));
            resultado.put("tramosActualizados", distancias.get("tramosActualizados"));
            resultado.put("costoTotal", costos.get("costoTotal"));
            resultado.put("tarifaPorKm", costos.get("tarifaPorKm"));
            resultado.put("costosPorTramo", costos.get("costosPorTramo"));
            resultado.put("exitoso", true);
            resultado.put("mensaje", String.format("Ruta calculada: %.2f km, %.2f horas, $%.2f", 
                resultado.get("distanciaTotal"), resultado.get("duracionTotalHoras"), resultado.get("costoTotal")));
            
            logger.info("POST /api/v1/rutas/{}/calcular-completo - Respuesta: 200 - Distancia: {} km, Costo: ${}", 
                id, resultado.get("distanciaTotal"), resultado.get("costoTotal"));
            return ResponseEntity.ok(resultado);
        } catch (IllegalArgumentException e) {
            logger.warn("POST /api/v1/rutas/{}/calcular-completo - Respuesta: 400 - {}", id, e.getMessage());
            java.util.Map<String, Object> error = new java.util.HashMap<>();
            error.put("exitoso", false);
            error.put("mensaje", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            logger.error("POST /api/v1/rutas/{}/calcular-completo - Respuesta: 500 - {}", id, e.getMessage());
            java.util.Map<String, Object> error = new java.util.HashMap<>();
            error.put("exitoso", false);
            error.put("mensaje", "Error al calcular ruta completa: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
}

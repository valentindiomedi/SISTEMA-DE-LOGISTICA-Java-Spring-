package com.backend.tpi.ms_rutas_transportistas.controllers;

import com.backend.tpi.ms_rutas_transportistas.dtos.TramoRequestDTO;
import com.backend.tpi.ms_rutas_transportistas.dtos.TramoDTO;
import com.backend.tpi.ms_rutas_transportistas.services.TramoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tramos")
public class TramoController {

    private static final Logger logger = LoggerFactory.getLogger(TramoController.class);

    @Autowired
    private TramoService tramoService;

    /**
     * Crea un nuevo tramo para una ruta
     * @param tramoRequestDTO Datos del tramo a crear
     * @return Tramo creado
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('OPERADOR','ADMIN')")
    public ResponseEntity<TramoDTO> create(@RequestBody TramoRequestDTO tramoRequestDTO) {
        logger.info("POST /api/v1/tramos - Creando nuevo tramo para ruta ID: {}", tramoRequestDTO.getIdRuta());
        TramoDTO tramo = tramoService.create(tramoRequestDTO);
        if (tramo == null) {
            logger.warn("POST /api/v1/tramos - Respuesta: 400 - Error al crear tramo");
            return ResponseEntity.badRequest().build();
        }
        logger.info("POST /api/v1/tramos - Respuesta: 200 - Tramo creado con ID: {}", tramo.getId());
        return ResponseEntity.ok(tramo);
    }

    /**
     * Obtiene la lista de todos los tramos
     * @return Lista de tramos
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('OPERADOR','TRANSPORTISTA','ADMIN','CLIENTE')")
    public List<TramoDTO> getAllTramos() {
        logger.info("GET /api/v1/tramos - Listando todos los tramos");
        List<TramoDTO> result = tramoService.findAll();
        logger.info("GET /api/v1/tramos - Respuesta: 200 - {} tramos encontrados", result.size());
        return result;
    }

    /**
     * Obtiene todos los tramos de una ruta específica
     * @param rutaId ID de la ruta
     * @return Lista de tramos de la ruta
     */
    @GetMapping("/por-ruta/{rutaId}")
    @PreAuthorize("hasAnyRole('OPERADOR','TRANSPORTISTA','ADMIN','CLIENTE')")
    public List<TramoDTO> getByRuta(@PathVariable Long rutaId) {
        logger.info("GET /api/v1/tramos/por-ruta/{} - Buscando tramos de la ruta", rutaId);
        List<TramoDTO> result = tramoService.findByRutaId(rutaId);
        logger.info("GET /api/v1/tramos/por-ruta/{} - Respuesta: 200 - {} tramos encontrados", rutaId, result.size());
        return result;
    }

    /**
     * Asigna un camión a un tramo específico, validando capacidad
     * @param id ID del tramo
     * @param camionId ID del camión a asignar
     * @return Tramo con camión asignado
     */
    @PostMapping("/{id}/asignar-transportista")
    @PreAuthorize("hasAnyRole('OPERADOR','ADMIN')")
    public ResponseEntity<?> asignarTransportista(@PathVariable Long id,
                                                 @RequestParam(required = false) Long camionId,
                                                 @RequestParam(required = false) String dominio) {
        logger.info("POST /api/v1/tramos/{}/asignar-transportista - Asignando camiónId: {} dominio: {}", id, camionId, dominio);
        
        try {
            TramoDTO dto;
            if (dominio != null && !dominio.isEmpty()) {
                dto = tramoService.assignTransportistaByDominio(id, dominio);
            } else {
                dto = tramoService.assignTransportista(id, camionId);
            }
            if (dto == null) {
                logger.warn("POST /api/v1/tramos/{}/asignar-transportista - Respuesta: 404 - Tramo no encontrado", id);
                return ResponseEntity.notFound().build();
            }
            logger.info("POST /api/v1/tramos/{}/asignar-transportista - Respuesta: 200 - Camión asignado", id);
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException e) {
            logger.error("POST /api/v1/tramos/{}/asignar-transportista - Respuesta: 400 - {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(java.util.Map.of(
                "error", "Validación de capacidad fallida",
                "mensaje", e.getMessage()
            ));
        } catch (Exception e) {
            logger.error("POST /api/v1/tramos/{}/asignar-transportista - Respuesta: 500 - Error inesperado: {}", id, e.getMessage());
            return ResponseEntity.internalServerError().body(java.util.Map.of(
                "error", "Error al asignar camión",
                "mensaje", e.getMessage()
            ));
        }
    }

    /**
     * Elimina un tramo del sistema
     * @param id ID del tramo a eliminar
     * @return Respuesta sin contenido
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERADOR','ADMIN')")
    public ResponseEntity<Void> deleteTramo(@PathVariable Long id) {
        logger.info("DELETE /api/v1/tramos/{} - Eliminando tramo", id);
        tramoService.delete(id);
        logger.info("DELETE /api/v1/tramos/{} - Respuesta: 204 - Tramo eliminado", id);
        return ResponseEntity.noContent().build();
    }

    /**
    * Actualiza la fecha de llegada de un tramo
    * Si es el último tramo, cambia el estado de la solicitud a COMPLETADA
     * @param id ID del tramo
     * @param fechaHoraReal Fecha y hora real de llegada (formato ISO: 2025-11-20T15:30:00)
     * @return Tramo actualizado
     */
    @PatchMapping("/{id}/fecha-llegada")
    @PreAuthorize("hasAnyRole('TRANSPORTISTA','OPERADOR','ADMIN')")
    public ResponseEntity<?> updateFechaLlegada(
            @PathVariable Long id,
            @RequestParam String fechaHoraReal) {
        logger.info("PATCH /api/v1/tramos/{}/fecha-llegada - Actualizando fecha: {}", id, fechaHoraReal);
        try {
            java.time.LocalDateTime fecha = java.time.LocalDateTime.parse(fechaHoraReal);
            TramoDTO tramo = tramoService.updateFechaLlegada(id, fecha);
            logger.info("PATCH /api/v1/tramos/{}/fecha-llegada - Respuesta: 200 - Fecha actualizada", id);
            return ResponseEntity.ok(tramo);
        } catch (RuntimeException e) {
            logger.error("PATCH /api/v1/tramos/{}/fecha-llegada - Respuesta: 404 - {}", id, e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("PATCH /api/v1/tramos/{}/fecha-llegada - Respuesta: 400 - Error al parsear fecha: {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(java.util.Map.of(
                "error", "Formato de fecha inválido",
                "mensaje", "Use formato ISO-8601: YYYY-MM-DDTHH:mm:ss"
            ));
        }
    }
}

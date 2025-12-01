package com.backend.tpi.ms_rutas_transportistas.controllers;

import com.backend.tpi.ms_rutas_transportistas.dtos.CamionDTO;
import com.backend.tpi.ms_rutas_transportistas.services.CamionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/camiones")
@Tag(name = "Camiones", description = "Gestión de camiones y transportistas")
public class CamionController {

    private static final Logger logger = LoggerFactory.getLogger(CamionController.class);

    @Autowired
    private CamionService camionService;

    /**
     * Obtiene la lista de todos los camiones registrados
     * @return Lista de camiones
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('OPERADOR','TRANSPORTISTA','ADMIN')")
    @Operation(summary = "Listar todos los camiones")
    public List<CamionDTO> getAllCamiones() {
        logger.info("GET /api/v1/camiones - Listando todos los camiones");
        List<CamionDTO> result = camionService.findAll();
        logger.info("GET /api/v1/camiones - Respuesta: 200 - {} camiones encontrados", result.size());
        return result;
    }

    /**
     * Obtiene un camión por su dominio o patente
     * @param dominio Dominio o patente del camión
     * @return Camión encontrado
     */
    @GetMapping("/{dominio}")
    @PreAuthorize("hasAnyRole('OPERADOR','TRANSPORTISTA','ADMIN')")
    @Operation(summary = "Obtener camión por dominio/patente")
    public ResponseEntity<CamionDTO> getCamionByDominio(@PathVariable String dominio) {
        logger.info("GET /api/v1/camiones/{} - Buscando camión por dominio", dominio);
        CamionDTO camion = camionService.findByDominio(dominio);
        logger.info("GET /api/v1/camiones/{} - Respuesta: 200 - Camión encontrado", dominio);
        return ResponseEntity.ok(camion);
    }

    /**
     * Registra un nuevo camión en el sistema
     * @param camion Datos del camión a registrar
     * @return Camión registrado
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('OPERADOR','ADMIN')")
    @Operation(summary = "Registrar nuevo camión con capacidad y costos")
    public CamionDTO createCamion(@RequestBody CamionDTO camion) {
        logger.info("POST /api/v1/camiones - Creando nuevo camión con dominio: {}", camion.getDominio());
        CamionDTO result = camionService.save(camion);
        logger.info("POST /api/v1/camiones - Respuesta: 200 - Camión creado con dominio: {}", result.getDominio());
        return result;
    }

    /**
     * Actualiza el estado operativo de un camión
     * @param dominio Dominio del camión
     * @param disponible Nuevo estado de disponibilidad
     * @param activo Nuevo estado de actividad
     * @return Camión con estado actualizado
     */
    @PostMapping("/{dominio}/estado")
    @PreAuthorize("hasAnyRole('TRANSPORTISTA','OPERADOR','ADMIN')")
    @Operation(summary = "Actualizar estado operativo del camión")
    public ResponseEntity<CamionDTO> updateEstado(
            @PathVariable String dominio,
            @RequestParam(required = false) Boolean disponible,
            @RequestParam(required = false) Boolean activo) {
        logger.info("POST /api/v1/camiones/{}/estado - Actualizando estado - disponible: {}, activo: {}", 
            dominio, disponible, activo);
        CamionDTO camion = camionService.updateEstado(dominio, disponible, activo);
        logger.info("POST /api/v1/camiones/{}/estado - Respuesta: 200 - Estado actualizado", dominio);
        return ResponseEntity.ok(camion);
    }

    /**
     * Asigna un camión a un transportista específico
     * @param dominio Dominio del camión
     * @param nombreTransportista Nombre del transportista
     * @return Camión con transportista asignado
     */
    @PatchMapping("/{dominio}/asignar")
    @PreAuthorize("hasAnyRole('OPERADOR','ADMIN')")
    @Operation(summary = "Asignar camión a un transportista")
    public ResponseEntity<CamionDTO> asignarTransportista(
            @PathVariable String dominio,
            @RequestParam String nombreTransportista) {
        logger.info("PATCH /api/v1/camiones/{}/asignar - Asignando a transportista: {}", dominio, nombreTransportista);
        CamionDTO camion = camionService.asignarTransportista(dominio, nombreTransportista);
        logger.info("PATCH /api/v1/camiones/{}/asignar - Respuesta: 200 - Transportista asignado", dominio);
        return ResponseEntity.ok(camion);
    }

    /**
     * Elimina un camión del sistema
     * @param dominio Dominio del camión a eliminar
     * @return Respuesta sin contenido
     */
    @DeleteMapping("/{dominio}")
    @PreAuthorize("hasAnyRole('OPERADOR','ADMIN')")
    @Operation(summary = "Eliminar camión del sistema")
    public ResponseEntity<Void> deleteCamion(@PathVariable String dominio) {
        logger.info("DELETE /api/v1/camiones/{} - Eliminando camión", dominio);
        camionService.deleteByDominio(dominio);
        logger.info("DELETE /api/v1/camiones/{} - Respuesta: 204 - Camión eliminado", dominio);
        return ResponseEntity.noContent().build();
    }
}

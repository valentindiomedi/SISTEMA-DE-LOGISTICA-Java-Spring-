package com.backend.tpi.ms_gestion_calculos.controllers;

import com.backend.tpi.ms_gestion_calculos.dtos.TarifaDTO;
import com.backend.tpi.ms_gestion_calculos.dtos.TarifaVolumenPesoDTO;
import com.backend.tpi.ms_gestion_calculos.services.TarifaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador REST para Tarifas
 * Permite gestionar tarifas base y sus rangos de volumen/peso
 */
@RestController
@RequestMapping("/api/v1/tarifas")
public class TarifaController {

    private static final Logger logger = LoggerFactory.getLogger(TarifaController.class);

    @Autowired
    private TarifaService tarifaService;

    /**
     * GET /api/v1/tarifas - Lista todas las tarifas del sistema
    * Requiere rol CLIENTE, OPERADOR o ADMIN
     * @return Lista de tarifas con sus rangos
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('CLIENTE','OPERADOR','ADMIN')")
    public List<TarifaDTO> getAllTarifas() {
        logger.info("GET /api/v1/tarifas - Listando todas las tarifas");
        List<TarifaDTO> result = tarifaService.findAll();
        logger.info("GET /api/v1/tarifas - Respuesta: 200 - {} tarifas encontradas", result.size());
        return result;
    }

    /**
     * GET /api/v1/tarifas/{id} - Obtiene una tarifa específica por ID
    * Requiere rol CLIENTE, OPERADOR o ADMIN
     * @param id ID de la tarifa
     * @return Tarifa con sus rangos
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLIENTE','OPERADOR','ADMIN')")
    public TarifaDTO getTarifaById(@PathVariable Long id) {
        logger.info("GET /api/v1/tarifas/{} - Buscando tarifa por ID", id);
        TarifaDTO result = tarifaService.findById(id);
        logger.info("GET /api/v1/tarifas/{} - Respuesta: 200 - Tarifa encontrada", id);
        return result;
    }

    /**
     * POST /api/v1/tarifas - Crea una nueva tarifa
    * Requiere rol OPERADOR o ADMIN
     * @param tarifaDto Datos de la tarifa a crear
     * @return Tarifa creada
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('OPERADOR','ADMIN')")
    public TarifaDTO createTarifa(@RequestBody TarifaDTO tarifaDto) {
        logger.info("POST /api/v1/tarifas - Creando nueva tarifa");
        TarifaDTO result = tarifaService.save(tarifaDto);
        logger.info("POST /api/v1/tarifas - Respuesta: 200 - Tarifa creada con ID: {}", result.getId());
        return result;
    }

    /**
     * PATCH /api/v1/tarifas/{id} - Actualiza una tarifa existente
    * Requiere rol ADMIN o OPERADOR
     * @param id ID de la tarifa a actualizar
     * @param tarifaDto Nuevos datos de la tarifa
     * @return Tarifa actualizada
     */
    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public TarifaDTO updateTarifa(@PathVariable Long id, @RequestBody TarifaDTO tarifaDto) {
        logger.info("PATCH /api/v1/tarifas/{} - Actualizando tarifa", id);
        TarifaDTO result = tarifaService.update(id, tarifaDto);
        logger.info("PATCH /api/v1/tarifas/{} - Respuesta: 200 - Tarifa actualizada", id);
        return result;
    }

    /**
     * POST /api/v1/tarifas/{id}/rango - Agrega un rango de volumen/peso a una tarifa
    * Requiere rol ADMIN o OPERADOR
     * @param id ID de la tarifa
     * @param rangoDto Datos del rango a agregar
     * @return Tarifa actualizada con el nuevo rango
     */
    @PostMapping("/{id}/rango")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public TarifaDTO addRango(@PathVariable Long id, @RequestBody TarifaVolumenPesoDTO rangoDto) {
        logger.info("POST /api/v1/tarifas/{}/rango - Agregando rango a tarifa", id);
        TarifaDTO result = tarifaService.addRango(id, rangoDto);
        logger.info("POST /api/v1/tarifas/{}/rango - Respuesta: 200 - Rango agregado", id);
        return result;
    }

    /**
     * PATCH /api/v1/tarifas/{idTarifa}/rango/{idRango} - Actualiza un rango de una tarifa
    * Requiere rol ADMIN o OPERADOR
     * @param idTarifa ID de la tarifa
     * @param idRango ID del rango a actualizar
     * @param rangoDto Nuevos datos del rango
     * @return Tarifa actualizada con el rango modificado
     */
    @PatchMapping("/{idTarifa}/rango/{idRango}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public TarifaDTO updateRango(
            @PathVariable Long idTarifa,
            @PathVariable Long idRango,
            @RequestBody TarifaVolumenPesoDTO rangoDto) {
        logger.info("PATCH /api/v1/tarifas/{}/rango/{} - Actualizando rango", idTarifa, idRango);
        TarifaDTO result = tarifaService.updateRango(idTarifa, idRango, rangoDto);
        logger.info("PATCH /api/v1/tarifas/{}/rango/{} - Respuesta: 200 - Rango actualizado", idTarifa, idRango);
        return result;
    }
}

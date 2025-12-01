package com.backend.tpi.ms_gestion_calculos.controllers;

import com.backend.tpi.ms_gestion_calculos.dtos.DepositoDTO;
import com.backend.tpi.ms_gestion_calculos.services.DepositoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador REST para Depósitos
 * Permite gestionar depósitos/almacenes del sistema
 */
@RestController
@RequestMapping("/api/v1/depositos")
public class DepositoController {

    private static final Logger logger = LoggerFactory.getLogger(DepositoController.class);

    @Autowired
    private DepositoService depositoService;

    /**
    * GET /api/v1/depositos - Lista todos los depósitos del sistema
    * Requiere rol CLIENTE, OPERADOR o ADMIN
     * @return Lista de depósitos
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('CLIENTE','OPERADOR','ADMIN')")
    public List<DepositoDTO> getAllDepositos() {
        logger.info("GET /api/v1/depositos - Listando todos los depósitos");
        List<DepositoDTO> result = depositoService.findAll();
        logger.info("GET /api/v1/depositos - Respuesta: 200 - {} depósitos encontrados", result.size());
        return result;
    }

    /**
    * POST /api/v1/depositos - Crea un nuevo depósito
    * Requiere rol OPERADOR o ADMIN
     * @param deposito Datos del depósito a crear
     * @return Depósito creado
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('OPERADOR','ADMIN')")
    public DepositoDTO createDeposito(@RequestBody DepositoDTO deposito) {
        logger.info("POST /api/v1/depositos - Creando nuevo depósito");
        DepositoDTO result = depositoService.save(deposito);
        logger.info("POST /api/v1/depositos - Respuesta: 200 - Depósito creado con ID: {}", result.getId());
        return result;
    }

    /**
    * GET /api/v1/depositos/{id} - Obtiene un depósito específico por ID
    * Requiere rol ADMIN u OPERADOR
     * @param id ID del depósito
     * @return Depósito encontrado
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public DepositoDTO getDepositoById(@PathVariable Long id) {
        logger.info("GET /api/v1/depositos/{} - Buscando depósito por ID", id);
        DepositoDTO result = depositoService.findById(id);
        logger.info("GET /api/v1/depositos/{} - Respuesta: 200 - Depósito encontrado", id);
        return result;
    }

    /**
    * PATCH /api/v1/depositos/{id} - Actualiza un depósito existente
    * Requiere rol ADMIN, OPERADOR
     * @param id ID del depósito a actualizar
     * @param depositoDto Nuevos datos del depósito
     * @return Depósito actualizado
     */
    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public DepositoDTO updateDeposito(@PathVariable Long id, @RequestBody DepositoDTO depositoDto) {
        logger.info("PATCH /api/v1/depositos/{} - Actualizando depósito", id);
        DepositoDTO result = depositoService.update(id, depositoDto);
        logger.info("PATCH /api/v1/depositos/{} - Respuesta: 200 - Depósito actualizado", id);
        return result;
    }

    /**
    * GET /api/v1/depositos/{id}/coordenadas - Obtiene las coordenadas de un depósito
    * Requiere rol OPERADOR o ADMIN
     * @param id ID del depósito
     * @return Coordenadas del depósito (latitud y longitud)
     */
    @GetMapping("/{id}/coordenadas")
    @PreAuthorize("hasAnyRole('OPERADOR','ADMIN','TRANSPORTISTA')")
    public org.springframework.http.ResponseEntity<java.util.Map<String, Object>> getCoordenadasDeposito(@PathVariable Long id) {
        logger.info("GET /api/v1/depositos/{}/coordenadas - Consultando coordenadas", id);
        try {
            DepositoDTO deposito = depositoService.findById(id);
            if (deposito.getLatitud() == null || deposito.getLongitud() == null) {
                logger.warn("GET /api/v1/depositos/{}/coordenadas - Respuesta: 404 - Depósito sin coordenadas", id);
                return org.springframework.http.ResponseEntity.notFound().build();
            }
            java.util.Map<String, Object> coordenadas = new java.util.HashMap<>();
            coordenadas.put("depositoId", id);
            coordenadas.put("nombre", deposito.getNombre());
            coordenadas.put("latitud", deposito.getLatitud());
            coordenadas.put("longitud", deposito.getLongitud());
            logger.info("GET /api/v1/depositos/{}/coordenadas - Respuesta: 200 - lat={}, lon={}", 
                    id, deposito.getLatitud(), deposito.getLongitud());
            return org.springframework.http.ResponseEntity.ok(coordenadas);
        } catch (RuntimeException e) {
            logger.warn("GET /api/v1/depositos/{}/coordenadas - Respuesta: 404 - Depósito no encontrado: {}", id, e.getMessage());
            return org.springframework.http.ResponseEntity.notFound().build();
        }
    }
}

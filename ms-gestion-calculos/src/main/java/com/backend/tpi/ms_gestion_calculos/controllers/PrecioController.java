package com.backend.tpi.ms_gestion_calculos.controllers;

import com.backend.tpi.ms_gestion_calculos.dtos.CostoRequestDTO;
import com.backend.tpi.ms_gestion_calculos.dtos.CostoResponseDTO;
import com.backend.tpi.ms_gestion_calculos.services.PrecioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST para cálculo de Precios
 * Expone endpoints para calcular costos estimados y reales de traslados
 */
@RestController
@RequestMapping("/api/v1/precio")
public class PrecioController {

    private static final Logger logger = LoggerFactory.getLogger(PrecioController.class);

    @Autowired
    private PrecioService precioService;

    /**
    * POST /api/v1/precio/estimado - Calcula un precio estimado para un traslado
    * Requiere rol CLIENTE u OPERADOR
     * @param request Datos del origen, destino, peso y volumen
     * @return Costo total estimado y tiempo estimado
     */
    @PostMapping("/estimado")
    @PreAuthorize("hasAnyRole('CLIENTE','OPERADOR')")
    public CostoResponseDTO getPrecioEstimado(@RequestBody CostoRequestDTO request) {
        logger.info("POST /api/v1/precio/estimado - Calculando precio estimado");
        CostoResponseDTO result = precioService.calcularCostoEstimado(request);
        logger.info("POST /api/v1/precio/estimado - Respuesta: 200 - Costo total: {}", result.getCostoTotal());
        return result;
    }

    /**
     * POST /api/v1/precio/traslado - Calcula el costo real de un traslado
     * Requiere rol ADMIN u OPERADOR
     * @param request Datos del traslado
     * @return Costo total y tiempo estimado
     */
    @PostMapping("/traslado")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public CostoResponseDTO getCostoTraslado(@RequestBody CostoRequestDTO request) {
        logger.info("POST /api/v1/precio/traslado - Calculando costo de traslado");
        CostoResponseDTO result = precioService.calcularCostoTraslado(request);
        logger.info("POST /api/v1/precio/traslado - Respuesta: 200 - Costo total: {}", result.getCostoTotal());
        return result;
    }

    /**
    * POST /api/v1/precio/solicitud/{id}/costo - Calcula el costo para una solicitud específica
    * Obtiene datos de la solicitud desde ms-solicitudes y calcula el costo
    * Requiere rol OPERADOR o ADMIN
     * @param id ID de la solicitud
     * @return Costo total calculado
     */
    @PostMapping("/solicitud/{id}/costo")
    @PreAuthorize("hasAnyRole('OPERADOR','ADMIN')")
    public CostoResponseDTO getCostoPorSolicitud(@PathVariable Long id) {
        logger.info("POST /api/v1/precio/solicitud/{}/costo - Calculando costo para solicitud", id);
        CostoResponseDTO result = precioService.calcularCostoParaSolicitud(id);
        logger.info("POST /api/v1/precio/solicitud/{}/costo - Respuesta: 200 - Costo total: {}", id, result.getCostoTotal());
        return result;
    }
}

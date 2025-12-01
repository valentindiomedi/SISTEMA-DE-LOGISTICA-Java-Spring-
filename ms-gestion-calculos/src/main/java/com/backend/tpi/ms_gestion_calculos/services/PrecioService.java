package com.backend.tpi.ms_gestion_calculos.services;

import com.backend.tpi.ms_gestion_calculos.dtos.CostoRequestDTO;
import com.backend.tpi.ms_gestion_calculos.dtos.CostoResponseDTO;
import com.backend.tpi.ms_gestion_calculos.dtos.DistanciaRequestDTO;
import com.backend.tpi.ms_gestion_calculos.dtos.DistanciaResponseDTO;
import com.backend.tpi.ms_gestion_calculos.dtos.SolicitudIntegrationDTO;
import com.backend.tpi.ms_gestion_calculos.models.Tarifa;
import com.backend.tpi.ms_gestion_calculos.models.TarifaVolumenPeso;
import com.backend.tpi.ms_gestion_calculos.repositories.TarifaRepository;
import com.backend.tpi.ms_gestion_calculos.repositories.TarifaVolumenPesoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Servicio de negocio para cálculo de Precios
 * Calcula costos estimados y reales basándose en tarifas, distancias y características de carga
 * Se integra con ms-solicitudes para obtener datos de solicitudes
 */
@Service
public class PrecioService {

    private static final Logger logger = LoggerFactory.getLogger(PrecioService.class);

    @Autowired
    private TarifaRepository tarifaRepository;

    @Autowired
    private TarifaVolumenPesoRepository tarifaVolumenPesoRepository;

    @Autowired
    private CalculoService calculoService;

    @Autowired
    private RestClient solicitudesClient;

    /**
     * Calcula un costo estimado en base a distancia, tarifas y una tabla por volumen/peso.
     * Algoritmo (simple): costo = costoBaseGestionFijo + precioPorKm * distancia + cargoPorVolumenPeso
     */
    public CostoResponseDTO calcularCostoEstimado(CostoRequestDTO request) {
        logger.info("Calculando costo estimado - origen: {}, destino: {}, peso: {}, volumen: {}", 
                request.getOrigen(), request.getDestino(), request.getPeso(), request.getVolumen());
        
        // obtener distancia usando el servicio de calculos
        logger.debug("Solicitando cálculo de distancia al servicio de cálculos");
        DistanciaRequestDTO distanciaReq = new DistanciaRequestDTO();
        distanciaReq.setOrigen(request.getOrigen());
        distanciaReq.setDestino(request.getDestino());
        DistanciaResponseDTO distanciaResp = calculoService.calcularDistancia(distanciaReq);
        double distancia = distanciaResp != null && distanciaResp.getDistancia() != null ? distanciaResp.getDistancia() : 0.0;
        logger.debug("Distancia obtenida: {} km", distancia);

        // obtener tarifa base (la más reciente si existe)
        logger.debug("Obteniendo tarifa base desde repositorio");
        Tarifa tarifa = tarifaRepository.findTopByOrderByIdDesc();
        double costoBase = tarifa != null && tarifa.getCostoBaseGestionFijo() != null ? tarifa.getCostoBaseGestionFijo().doubleValue() : 0.0;
        double precioPorKm = tarifa != null && tarifa.getValorLitroCombustible() != null ? tarifa.getValorLitroCombustible().doubleValue() : 1.0;
        logger.debug("Tarifa aplicada - costoBase: {}, precioPorKm: {}", costoBase, precioPorKm);

        // buscar cargo por volumen/peso aplicable
        logger.debug("Buscando cargo por volumen/peso aplicable");
        List<TarifaVolumenPeso> tvps = tarifaVolumenPesoRepository.findAll();
        double cargoVolumenPeso = 0.0;
        if (tvps != null && !tvps.isEmpty()) {
            for (TarifaVolumenPeso t : tvps) {
                boolean aplicaPeso = request.getPeso() == null || t.getPesoMax() == null || request.getPeso() <= t.getPesoMax();
                boolean aplicaVolumen = request.getVolumen() == null || t.getVolumenMax() == null || request.getVolumen() <= t.getVolumenMax();
                if (aplicaPeso && aplicaVolumen) {
                    cargoVolumenPeso = t.getCostoPorKmBase() != null ? t.getCostoPorKmBase() : 0.0;
                    logger.debug("Cargo por volumen/peso encontrado: {}", cargoVolumenPeso);
                    break;
                }
            }
        }

        double costo = costoBase + (precioPorKm * distancia) + cargoVolumenPeso;
        logger.debug("Costo calculado (antes de redondeo): {}", costo);

        // redondear a 2 decimales
        BigDecimal bd = BigDecimal.valueOf(costo).setScale(2, RoundingMode.HALF_UP);

        // estimar tiempo: velocidad promedio 60 km/h
        String tiempoEstimado = "N/A";
        if (distancia > 0) {
            double horas = distancia / 60.0;
            int h = (int) horas;
            int minutos = (int) Math.round((horas - h) * 60);
            tiempoEstimado = String.format("%dh %02dm", h, minutos);
        }
        logger.debug("Tiempo estimado calculado: {}", tiempoEstimado);

        CostoResponseDTO resp = new CostoResponseDTO();
        resp.setCostoTotal(bd.doubleValue());
        resp.setTiempoEstimado(tiempoEstimado);
        logger.info("Costo estimado calculado exitosamente - total: {}, tiempo: {}", bd.doubleValue(), tiempoEstimado);
        return resp;
    }

    /**
     * Integra con ms-solicitudes para obtener los datos de la solicitud y calcular el costo.
     * Si falla la comunicación, cae a un cálculo por defecto como fallback.
     */
    public CostoResponseDTO calcularCostoParaSolicitud(Long solicitudId) {
        logger.info("Calculando costo para solicitud ID: {}", solicitudId);
        try {
            logger.debug("Consultando ms-solicitudes para obtener datos de solicitud ID: {}", solicitudId);
            String token = extractBearerToken();
            ResponseEntity<SolicitudIntegrationDTO> solicitudEntity = solicitudesClient.get()
                    .uri("/api/v1/solicitudes/{id}", solicitudId)
                    .headers(h -> { if (token != null) h.setBearerAuth(token); })
                    .retrieve()
                    .toEntity(SolicitudIntegrationDTO.class);

            SolicitudIntegrationDTO solicitud = solicitudEntity != null ? solicitudEntity.getBody() : null;
            if (solicitud != null) {
                logger.debug("Solicitud obtenida exitosamente - origen: {}, destino: {}", 
                        solicitud.getDireccionOrigen(), solicitud.getDireccionDestino());
                CostoRequestDTO req = new CostoRequestDTO();
                req.setOrigen(solicitud.getDireccionOrigen());
                req.setDestino(solicitud.getDireccionDestino());
                // ms-solicitudes no expone peso/volumen por ahora -> usar valores por defecto
                req.setPeso(1000.0);
                req.setVolumen(10.0);
                logger.debug("Usando valores por defecto - peso: 1000.0, volumen: 10.0");
                CostoResponseDTO resp = calcularCostoEstimado(req);
                resp.setTiempoEstimado(resp.getTiempoEstimado() + " (calculado desde ms-solicitudes)");
                logger.info("Costo calculado exitosamente para solicitud ID: {} - costo: {}", 
                        solicitudId, resp.getCostoTotal());
                return resp;
            } else {
                logger.warn("Respuesta vacía de ms-solicitudes para solicitud ID: {}", solicitudId);
            }
        } catch (Exception ex) {
            logger.error("Error al consultar ms-solicitudes para solicitud ID: {} - {}", 
                    solicitudId, ex.getMessage());
        }

        // Fallback: estimación por defecto
        logger.warn("Usando cálculo fallback para solicitud ID: {}", solicitudId);
        CostoRequestDTO fallback = new CostoRequestDTO();
        // No usar nombres de ciudades como valor por defecto; dejar campos vacíos
        // para que la lógica superior maneje la ausencia de coordenadas.
        fallback.setOrigen("");
        fallback.setDestino("");
        fallback.setPeso(1000.0);
        fallback.setVolumen(10.0);
        CostoResponseDTO resp = calcularCostoEstimado(fallback);
        resp.setTiempoEstimado(resp.getTiempoEstimado() + " (estimado - fallback)");
        logger.info("Costo fallback calculado para solicitud ID: {} - costo: {}", 
                solicitudId, resp.getCostoTotal());
        return resp;
    }

    /**
     * Calcula el costo real de un traslado (similar a estimado pero puede incluir lógica adicional)
     */
    public CostoResponseDTO calcularCostoTraslado(CostoRequestDTO request) {
        logger.info("Calculando costo de traslado - origen: {}, destino: {}", 
                request.getOrigen(), request.getDestino());
        // Por ahora usa la misma lógica que el estimado
        // En el futuro podría incluir costos adicionales, recargos, etc.
        CostoResponseDTO resp = calcularCostoEstimado(request);
        logger.info("Costo de traslado calculado: {}", resp.getCostoTotal());
        return resp;
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
}

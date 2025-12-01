package com.backend.tpi.ms_gestion_calculos.services;

import com.backend.tpi.ms_gestion_calculos.dtos.TarifaDTO;
import com.backend.tpi.ms_gestion_calculos.dtos.TarifaVolumenPesoDTO;
import com.backend.tpi.ms_gestion_calculos.models.Tarifa;
import com.backend.tpi.ms_gestion_calculos.models.TarifaVolumenPeso;
import com.backend.tpi.ms_gestion_calculos.repositories.TarifaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio de negocio para Tarifas
 * Gestiona tarifas base y sus rangos de volumen/peso asociados
 */
@Service
public class TarifaService {

    private static final Logger logger = LoggerFactory.getLogger(TarifaService.class);

    @Autowired
    private TarifaRepository tarifaRepository;

    /**
     * Obtiene todas las tarifas del sistema
     * @return Lista de DTOs de tarifas con sus rangos
     */
    public List<TarifaDTO> findAll() {
        logger.info("Obteniendo todas las tarifas");
        List<TarifaDTO> tarifas = tarifaRepository.findAll().stream()
                .map(this::toDto)
                .toList();
        logger.debug("Tarifas obtenidas: {}", tarifas.size());
        return tarifas;
    }

    /**
     * Busca una tarifa por su ID
     * @param id ID de la tarifa
     * @return DTO de la tarifa encontrada
     * @throws RuntimeException si no se encuentra la tarifa
     */
    public TarifaDTO findById(Long id) {
        logger.info("Buscando tarifa por ID: {}", id);
        Tarifa tarifa = tarifaRepository.findById(id)
                .orElseThrow(() -> {
                    logger.warn("Tarifa no encontrada con ID: {}", id);
                    return new RuntimeException("Tarifa no encontrada con id: " + id);
                });
        logger.debug("Tarifa encontrada: ID={}", tarifa.getId());
        return toDto(tarifa);
    }

    /**
     * Crea una nueva tarifa
     * @param dto Datos de la tarifa a crear
     * @return DTO de la tarifa creada
     */
    public TarifaDTO save(TarifaDTO dto) {
        logger.info("Creando nueva tarifa");
        Tarifa tarifa = toEntity(dto);
        Tarifa saved = tarifaRepository.save(tarifa);
        logger.info("Tarifa creada exitosamente con ID: {}", saved.getId());
        return toDto(saved);
    }

    /**
     * Actualiza una tarifa existente
     * @param id ID de la tarifa a actualizar
     * @param dto Nuevos datos de la tarifa
     * @return DTO de la tarifa actualizada
     */
    @Transactional
    public TarifaDTO update(Long id, TarifaDTO dto) {
        logger.info("Actualizando tarifa ID: {}", id);
        Tarifa tarifa = tarifaRepository.findById(id)
                .orElseThrow(() -> {
                    logger.warn("No se pudo actualizar - Tarifa no encontrada con ID: {}", id);
                    return new RuntimeException("Tarifa no encontrada con id: " + id);
                });
        
        if (dto.getCostoBaseGestionFijo() != null) {
            logger.debug("Actualizando costoBaseGestionFijo: {}", dto.getCostoBaseGestionFijo());
            tarifa.setCostoBaseGestionFijo(java.math.BigDecimal.valueOf(dto.getCostoBaseGestionFijo()));
        }
        if (dto.getValorLitroCombustible() != null) {
            logger.debug("Actualizando valorLitroCombustible: {}", dto.getValorLitroCombustible());
            tarifa.setValorLitroCombustible(java.math.BigDecimal.valueOf(dto.getValorLitroCombustible()));
        }
        
        Tarifa saved = tarifaRepository.save(tarifa);
        logger.info("Tarifa actualizada exitosamente: ID={}", saved.getId());
        return toDto(saved);
    }

    /**
     * Agrega un nuevo rango de volumen/peso a una tarifa existente
     * @param tarifaId ID de la tarifa
     * @param rangoDto Datos del rango a agregar
     * @return DTO de la tarifa actualizada con el nuevo rango
     */
    @Transactional
    public TarifaDTO addRango(Long tarifaId, TarifaVolumenPesoDTO rangoDto) {
        logger.info("Agregando rango a tarifa ID: {}", tarifaId);
        Tarifa tarifa = tarifaRepository.findById(tarifaId)
                .orElseThrow(() -> {
                    logger.warn("No se pudo agregar rango - Tarifa no encontrada con ID: {}", tarifaId);
                    return new RuntimeException("Tarifa no encontrada con id: " + tarifaId);
                });
        
        logger.debug("Creando nuevo rango - volumen: {}-{}, peso: {}-{}", 
                rangoDto.getVolumenMin(), rangoDto.getVolumenMax(), 
                rangoDto.getPesoMin(), rangoDto.getPesoMax());
        TarifaVolumenPeso rango = new TarifaVolumenPeso();
        rango.setTarifa(tarifa);
        rango.setVolumenMin(rangoDto.getVolumenMin());
        rango.setVolumenMax(rangoDto.getVolumenMax());
        rango.setPesoMin(rangoDto.getPesoMin());
        rango.setPesoMax(rangoDto.getPesoMax());
        rango.setCostoPorKmBase(rangoDto.getCostoPorKmBase());
        
        tarifa.getRangos().add(rango);
        Tarifa saved = tarifaRepository.save(tarifa);
        logger.info("Rango agregado exitosamente a tarifa ID: {}", tarifaId);
        return toDto(saved);
    }

    /**
     * Actualiza un rango existente de una tarifa
     * @param tarifaId ID de la tarifa
     * @param rangoId ID del rango a actualizar
     * @param rangoDto Nuevos datos del rango
     * @return DTO de la tarifa con el rango actualizado
     */
    @Transactional
    public TarifaDTO updateRango(Long tarifaId, Long rangoId, TarifaVolumenPesoDTO rangoDto) {
        logger.info("Actualizando rango ID: {} de tarifa ID: {}", rangoId, tarifaId);
        Tarifa tarifa = tarifaRepository.findById(tarifaId)
                .orElseThrow(() -> {
                    logger.warn("No se pudo actualizar rango - Tarifa no encontrada con ID: {}", tarifaId);
                    return new RuntimeException("Tarifa no encontrada con id: " + tarifaId);
                });
        
        TarifaVolumenPeso rango = tarifa.getRangos().stream()
                .filter(r -> r.getId().equals(rangoId))
                .findFirst()
                .orElseThrow(() -> {
                    logger.warn("No se pudo actualizar - Rango no encontrado con ID: {}", rangoId);
                    return new RuntimeException("Rango no encontrado con id: " + rangoId);
                });
        
        if (rangoDto.getVolumenMin() != null) {
            logger.debug("Actualizando volumenMin: {}", rangoDto.getVolumenMin());
            rango.setVolumenMin(rangoDto.getVolumenMin());
        }
        if (rangoDto.getVolumenMax() != null) {
            logger.debug("Actualizando volumenMax: {}", rangoDto.getVolumenMax());
            rango.setVolumenMax(rangoDto.getVolumenMax());
        }
        if (rangoDto.getPesoMin() != null) {
            logger.debug("Actualizando pesoMin: {}", rangoDto.getPesoMin());
            rango.setPesoMin(rangoDto.getPesoMin());
        }
        if (rangoDto.getPesoMax() != null) {
            logger.debug("Actualizando pesoMax: {}", rangoDto.getPesoMax());
            rango.setPesoMax(rangoDto.getPesoMax());
        }
        if (rangoDto.getCostoPorKmBase() != null) {
            logger.debug("Actualizando costoPorKmBase: {}", rangoDto.getCostoPorKmBase());
            rango.setCostoPorKmBase(rangoDto.getCostoPorKmBase());
        }
        
        Tarifa saved = tarifaRepository.save(tarifa);
        logger.info("Rango actualizado exitosamente: ID={}", rangoId);
        return toDto(saved);
    }

    /**
     * Convierte una entidad Tarifa a su DTO
     * @param tarifa Entidad tarifa
     * @return DTO de la tarifa
     */
    private TarifaDTO toDto(Tarifa tarifa) {
        if (tarifa == null) return null;
        TarifaDTO dto = new TarifaDTO();
        dto.setId(tarifa.getId());
        if (tarifa.getCostoBaseGestionFijo() != null) {
            dto.setCostoBaseGestionFijo(tarifa.getCostoBaseGestionFijo().doubleValue());
        }
        if (tarifa.getValorLitroCombustible() != null) {
            dto.setValorLitroCombustible(tarifa.getValorLitroCombustible().doubleValue());
        }
        if (tarifa.getRangos() != null && !tarifa.getRangos().isEmpty()) {
            dto.setRangos(tarifa.getRangos().stream()
                    .map(this::rangoToDto)
                    .collect(Collectors.toList()));
        }
        return dto;
    }

    /**
     * Convierte una entidad TarifaVolumenPeso a su DTO
     * @param rango Entidad rango
     * @return DTO del rango
     */
    private TarifaVolumenPesoDTO rangoToDto(TarifaVolumenPeso rango) {
        if (rango == null) return null;
        TarifaVolumenPesoDTO dto = new TarifaVolumenPesoDTO();
        dto.setId(rango.getId());
        dto.setVolumenMin(rango.getVolumenMin());
        dto.setVolumenMax(rango.getVolumenMax());
        dto.setPesoMin(rango.getPesoMin());
        dto.setPesoMax(rango.getPesoMax());
        dto.setCostoPorKmBase(rango.getCostoPorKmBase());
        return dto;
    }

    /**
     * Convierte un DTO de Tarifa a entidad
     * @param dto DTO de tarifa
     * @return Entidad tarifa
     */
    private Tarifa toEntity(TarifaDTO dto) {
        if (dto == null) return null;
        Tarifa tarifa = new Tarifa();
        tarifa.setId(dto.getId());
        if (dto.getCostoBaseGestionFijo() != null) {
            tarifa.setCostoBaseGestionFijo(java.math.BigDecimal.valueOf(dto.getCostoBaseGestionFijo()));
        }
        if (dto.getValorLitroCombustible() != null) {
            tarifa.setValorLitroCombustible(java.math.BigDecimal.valueOf(dto.getValorLitroCombustible()));
        }
        return tarifa;
    }
}

package com.backend.tpi.ms_gestion_calculos.services;

import com.backend.tpi.ms_gestion_calculos.dtos.TarifaVolumenPesoDTO;
import com.backend.tpi.ms_gestion_calculos.models.TarifaVolumenPeso;
import com.backend.tpi.ms_gestion_calculos.repositories.TarifaVolumenPesoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Servicio de negocio para Tarifas por Volumen y Peso
 * Gestiona los rangos de tarifas según volumen y peso de la carga
 */
@Service
public class TarifaVolumenPesoService {

    @Autowired
    private TarifaVolumenPesoRepository repository;

    /**
     * Obtiene todos los rangos de tarifas por volumen/peso
     * @return Lista de DTOs de rangos
     */
    public List<TarifaVolumenPesoDTO> findAll() {
        return repository.findAll().stream().map(this::toDto).toList();
    }

    /**
     * Busca un rango de tarifa por su ID
     * @param id ID del rango
     * @return DTO del rango encontrado, o null si no existe
     */
    public TarifaVolumenPesoDTO findById(Long id) {
        Optional<TarifaVolumenPeso> opt = repository.findById(id);
        return opt.map(this::toDto).orElse(null);
    }

    /**
     * Crea un nuevo rango de tarifa por volumen/peso
     * @param dto Datos del rango a crear
     * @return DTO del rango creado
     */
    public TarifaVolumenPesoDTO save(TarifaVolumenPesoDTO dto) {
        TarifaVolumenPeso e = toEntity(dto);
        TarifaVolumenPeso saved = repository.save(e);
        return toDto(saved);
    }

    /**
     * Actualiza un rango de tarifa existente
     * @param id ID del rango a actualizar
     * @param dto Nuevos datos del rango
     * @return DTO del rango actualizado, o null si no existe
     */
    public TarifaVolumenPesoDTO update(Long id, TarifaVolumenPesoDTO dto) {
        return repository.findById(id).map(existing -> {
            if (dto.getVolumenMin() != null) existing.setVolumenMin(dto.getVolumenMin());
            if (dto.getVolumenMax() != null) existing.setVolumenMax(dto.getVolumenMax());
            if (dto.getPesoMin() != null) existing.setPesoMin(dto.getPesoMin());
            if (dto.getPesoMax() != null) existing.setPesoMax(dto.getPesoMax());
            if (dto.getCostoPorKmBase() != null) existing.setCostoPorKmBase(dto.getCostoPorKmBase());
            TarifaVolumenPeso saved = repository.save(existing);
            return toDto(saved);
        }).orElse(null);
    }

    /**
     * Elimina un rango de tarifa por su ID
     * @param id ID del rango a eliminar
     */
    public void delete(Long id) {
        repository.deleteById(id);
    }

    /**
     * Convierte una entidad TarifaVolumenPeso a su DTO
     * @param e Entidad rango
     * @return DTO del rango
     */
    private TarifaVolumenPesoDTO toDto(TarifaVolumenPeso e) {
        if (e == null) return null;
        TarifaVolumenPesoDTO dto = new TarifaVolumenPesoDTO();
        dto.setId(e.getId());
        dto.setVolumenMin(e.getVolumenMin());
        dto.setVolumenMax(e.getVolumenMax());
        dto.setPesoMin(e.getPesoMin());
        dto.setPesoMax(e.getPesoMax());
        dto.setCostoPorKmBase(e.getCostoPorKmBase());
        return dto;
    }

    /**
     * Convierte un DTO de TarifaVolumenPeso a entidad
     * @param dto DTO del rango
     * @return Entidad rango
     */
    private TarifaVolumenPeso toEntity(TarifaVolumenPesoDTO dto) {
        if (dto == null) return null;
        TarifaVolumenPeso e = new TarifaVolumenPeso();
        e.setId(dto.getId());
        e.setVolumenMin(dto.getVolumenMin());
        e.setVolumenMax(dto.getVolumenMax());
        e.setPesoMin(dto.getPesoMin());
        e.setPesoMax(dto.getPesoMax());
        e.setCostoPorKmBase(dto.getCostoPorKmBase());
        return e;
    }
}

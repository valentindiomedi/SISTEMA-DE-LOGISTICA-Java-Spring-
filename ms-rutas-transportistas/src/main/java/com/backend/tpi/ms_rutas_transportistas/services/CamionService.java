package com.backend.tpi.ms_rutas_transportistas.services;

import com.backend.tpi.ms_rutas_transportistas.dtos.CamionDTO;
import com.backend.tpi.ms_rutas_transportistas.models.Camion;
import com.backend.tpi.ms_rutas_transportistas.repositories.CamionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CamionService {

    private static final Logger logger = LoggerFactory.getLogger(CamionService.class);

    @Autowired
    private CamionRepository camionRepository;

    /**
     * Obtiene la lista de todos los camiones registrados en el sistema
     * @return Lista de camiones como DTOs
     */
    public List<CamionDTO> findAll() {
        logger.debug("Buscando todos los camiones");
        List<CamionDTO> camiones = camionRepository.findAll().stream()
                .map(this::toDto)
                .toList();
        logger.debug("Encontrados {} camiones", camiones.size());
        return camiones;
    }

    /**
     * Registra un nuevo camión en el sistema
     * @param dto Datos del camión a registrar
     * @return Camión registrado como DTO
     * @throws IllegalArgumentException si los datos son inválidos o el dominio ya existe
     */
    public CamionDTO save(CamionDTO dto) {
        // Validar datos de entrada
        if (dto == null) {
            logger.error("CamionDTO no puede ser null");
            throw new IllegalArgumentException("Los datos del camión no pueden ser null");
        }
        
        if (dto.getDominio() == null || dto.getDominio().trim().isEmpty()) {
            logger.error("Dominio del camión no puede ser null o vacío");
            throw new IllegalArgumentException("El dominio del camión es obligatorio");
        }
        
        // Validar que no exista un camión con el mismo dominio (consulta directa)
        if (camionRepository.findFirstByDominio(dto.getDominio()).isPresent()) {
            logger.error("Ya existe un camión con dominio: {}", dto.getDominio());
            throw new IllegalArgumentException("Ya existe un camión registrado con el dominio: " + dto.getDominio());
        }
        
        // Validar capacidades
        if (dto.getCapacidadPesoMax() != null && dto.getCapacidadPesoMax() <= 0) {
            logger.error("Capacidad de peso inválida: {}", dto.getCapacidadPesoMax());
            throw new IllegalArgumentException("La capacidad de peso debe ser mayor a 0");
        }
        if (dto.getCapacidadVolumenMax() != null && dto.getCapacidadVolumenMax() <= 0) {
            logger.error("Capacidad de volumen inválida: {}", dto.getCapacidadVolumenMax());
            throw new IllegalArgumentException("La capacidad de volumen debe ser mayor a 0");
        }
        
        // Validar costos
        if (dto.getCostoBase() != null && dto.getCostoBase() < 0) {
            logger.error("Costo base inválido: {}", dto.getCostoBase());
            throw new IllegalArgumentException("El costo base no puede ser negativo");
        }
        if (dto.getCostoPorKm() != null && dto.getCostoPorKm() < 0) {
            logger.error("Costo por km inválido: {}", dto.getCostoPorKm());
            throw new IllegalArgumentException("El costo por km no puede ser negativo");
        }
        
        logger.info("Guardando nuevo camión con dominio: {}", dto.getDominio());
        Camion camion = toEntity(dto);
        Camion saved = camionRepository.save(camion);
        logger.info("Camión guardado exitosamente con ID: {}", saved.getId());
        return toDto(saved);
    }

    /**
     * Busca un camión por su dominio o patente
     * @param dominio Dominio o patente del camión a buscar
     * @return Camión encontrado como DTO
     * @throws RuntimeException si no se encuentra el camión
     */
    @Transactional(readOnly = true)
    public CamionDTO findByDominio(String dominio) {
        logger.debug("Buscando camión por dominio: {}", dominio);
        java.util.Optional<Camion> camionOpt = camionRepository.findFirstByDominio(dominio);
        if (camionOpt.isEmpty()) {
            logger.error("Camión no encontrado con dominio: {}", dominio);
            throw new RuntimeException("Camión no encontrado con dominio: " + dominio);
        }
        return toDto(camionOpt.get());
    }

    /**
     * Actualiza el estado operativo de un camión (disponibilidad y actividad)
     * @param dominio Dominio del camión
     * @param disponible Nuevo estado de disponibilidad (null para no modificar)
     * @param activo Nuevo estado de actividad (null para no modificar)
     * @return Camión actualizado como DTO
     * @throws RuntimeException si no se encuentra el camión
     * @throws IllegalArgumentException si ambos parámetros son null
     */
    @Transactional
    public CamionDTO updateEstado(String dominio, Boolean disponible, Boolean activo) {
        // Validar que al menos un parámetro sea no nulo
        if (disponible == null && activo == null) {
            logger.error("Ambos parámetros disponible y activo son null");
            throw new IllegalArgumentException("Debe especificar al menos un estado a actualizar (disponible o activo)");
        }
        
        logger.info("Actualizando estado de camión con dominio: {} - disponible: {}, activo: {}", dominio, disponible, activo);
        Camion camion = camionRepository.findFirstByDominio(dominio)
                .orElseThrow(() -> {
                    logger.error("Camión no encontrado con dominio: {}", dominio);
                    return new RuntimeException("Camión no encontrado con dominio: " + dominio);
                });
        
        if (disponible != null) {
            camion.setDisponible(disponible);
        }
        if (activo != null) {
            camion.setActivo(activo);
        }
        Camion saved = camionRepository.save(camion);
        logger.info("Estado del camión actualizado exitosamente - dominio: {}", dominio);
        return toDto(saved);
    }

    /**
     * Asigna un transportista a un camión específico
     * @param dominio Dominio o patente del camión
     * @param nombreTransportista Nombre del transportista a asignar
     * @return Camión con transportista asignado como DTO
     * @throws RuntimeException si no se encuentra el camión
     */
    @Transactional
    public CamionDTO asignarTransportista(String dominio, String nombreTransportista) {
        logger.info("Asignando transportista '{}' al camión con dominio: {}", nombreTransportista, dominio);
        Camion camion = camionRepository.findFirstByDominio(dominio)
                .orElseThrow(() -> {
                    logger.error("Camión no encontrado con dominio: {}", dominio);
                    return new RuntimeException("Camión no encontrado con dominio: " + dominio);
                });
        
        camion.setNombreTransportista(nombreTransportista);
        Camion saved = camionRepository.save(camion);
        logger.info("Transportista asignado exitosamente al camión con dominio: {}", dominio);
        return toDto(saved);
    }

    /**
     * Convierte una entidad Camion a DTO
     * @param camion Entidad a convertir
     * @return DTO con los datos del camión
     */
    private CamionDTO toDto(Camion camion) {
        if (camion == null) return null;
        CamionDTO dto = new CamionDTO();
        dto.setId(camion.getId());
        dto.setDominio(camion.getDominio());
        dto.setMarca(camion.getMarca());
        dto.setModelo(camion.getModelo());
        dto.setCapacidadPesoMax(camion.getCapacidadPesoMax());
        dto.setCapacidadVolumenMax(camion.getCapacidadVolumenMax());
        dto.setNombreTransportista(camion.getNombreTransportista());
        dto.setCostoBase(camion.getCostoBase());
        dto.setCostoPorKm(camion.getCostoPorKm());
        dto.setConsumoCombustiblePromedio(camion.getConsumoCombustiblePromedio());
        dto.setNumeroTransportistas(camion.getNumeroTransportistas());
        dto.setDisponible(camion.getDisponible());
        dto.setActivo(camion.getActivo());
        
        return dto;
    }

    /**
     * Convierte un DTO a entidad Camion
     * @param dto DTO a convertir
     * @return Entidad Camion
     */
    private Camion toEntity(CamionDTO dto) {
        if (dto == null) return null;
        Camion camion = new Camion();
        camion.setId(dto.getId());
        camion.setDominio(dto.getDominio());
        camion.setMarca(dto.getMarca());
        camion.setModelo(dto.getModelo());
        camion.setCapacidadPesoMax(dto.getCapacidadPesoMax());
        camion.setCapacidadVolumenMax(dto.getCapacidadVolumenMax());
        camion.setNombreTransportista(dto.getNombreTransportista());
        camion.setCostoBase(dto.getCostoBase());
        camion.setConsumoCombustiblePromedio(dto.getConsumoCombustiblePromedio());
        camion.setCostoPorKm(dto.getCostoPorKm());
        camion.setNumeroTransportistas(dto.getNumeroTransportistas());
        camion.setDisponible(dto.getDisponible() != null ? dto.getDisponible() : true);
        camion.setActivo(dto.getActivo() != null ? dto.getActivo() : true);
        return camion;
    }

    /**
     * Elimina un camión por su dominio
     * @param dominio Dominio del camión a eliminar
     * @throws RuntimeException si no se encuentra el camión
     */
    @Transactional
    public void deleteByDominio(String dominio) {
        logger.info("Eliminando camión con dominio: {}", dominio);
        Camion camion = camionRepository.findFirstByDominio(dominio)
                .orElseThrow(() -> {
                    logger.error("Camión no encontrado con dominio: {}", dominio);
                    return new RuntimeException("Camión no encontrado con dominio: " + dominio);
                });
        camionRepository.delete(camion);
        logger.info("Camión eliminado exitosamente con dominio: {}", dominio);
    }
}

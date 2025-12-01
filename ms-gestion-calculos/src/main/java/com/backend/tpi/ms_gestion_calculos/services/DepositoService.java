package com.backend.tpi.ms_gestion_calculos.services;

import com.backend.tpi.ms_gestion_calculos.dtos.DepositoDTO;
import com.backend.tpi.ms_gestion_calculos.models.Ciudad;
import com.backend.tpi.ms_gestion_calculos.models.Deposito;
import com.backend.tpi.ms_gestion_calculos.repositories.CiudadRepository;
import com.backend.tpi.ms_gestion_calculos.repositories.DepositoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Servicio de negocio para Depósitos
 * Gestiona operaciones CRUD de depósitos/almacenes
 */
@Service
public class DepositoService {

    private static final Logger logger = LoggerFactory.getLogger(DepositoService.class);

    @Autowired
    private DepositoRepository depositoRepository;

    @Autowired
    private CiudadRepository ciudadRepository;

    @Autowired
    private NominatimService nominatimService;

    /**
     * Obtiene todos los depósitos del sistema
     * @return Lista de DTOs de depósitos
     */
    public List<DepositoDTO> findAll() {
        logger.info("Obteniendo todos los depósitos");
        List<DepositoDTO> depositos = depositoRepository.findAll().stream()
                .map(this::toDto)
                .toList();
        logger.debug("Depósitos obtenidos: {}", depositos.size());
        return depositos;
    }

    /**
     * Crea un nuevo depósito
     * Automáticamente detecta y asigna la ciudad usando geocodificación inversa si no se proporciona
     * Prioridad: nombreCiudad > idCiudad > geocodificación automática
     * @param dto Datos del depósito a crear
     * @return DTO del depósito creado
     */
    public DepositoDTO save(DepositoDTO dto) {
        logger.info("Creando nuevo depósito: {}", dto.getNombre());
        Deposito deposito = toEntity(dto);
        
        Ciudad ciudad = null;
        
        // Prioridad 1: Si se proporciona nombre de ciudad, buscarla o crearla (estandarizado)
        if (dto.getNombreCiudad() != null && !dto.getNombreCiudad().trim().isEmpty()) {
            String nombreCiudad = estandarizarNombreCiudad(dto.getNombreCiudad());
            ciudad = obtenerOCrearCiudadPorNombre(nombreCiudad);
            logger.info("Ciudad asignada por nombre: {} (ID: {})", ciudad.getNombre(), ciudad.getId());
        }
        // Prioridad 2: Si se proporciona ID de ciudad, usarlo
        else if (dto.getIdCiudad() != null) {
            ciudad = ciudadRepository.findById(dto.getIdCiudad())
                .orElseThrow(() -> new RuntimeException("Ciudad no encontrada con id: " + dto.getIdCiudad()));
            logger.info("Ciudad asignada por ID: {} (ID: {})", ciudad.getNombre(), ciudad.getId());
        }
        // Prioridad 3: Si hay coordenadas, intentar geocodificación inversa
        else if (dto.getLatitud() != null && dto.getLongitud() != null) {
            logger.info("Intentando geocodificación automática para coordenadas lat={}, lon={}", 
                dto.getLatitud(), dto.getLongitud());
            ciudad = obtenerOCrearCiudadPorCoordenadas(
                BigDecimal.valueOf(dto.getLatitud()), 
                BigDecimal.valueOf(dto.getLongitud())
            );
            
            if (ciudad != null) {
                logger.info("Ciudad asignada automáticamente: {} (ID: {})", ciudad.getNombre(), ciudad.getId());
            } else {
                logger.warn("No se pudo determinar la ciudad automáticamente. El depósito se creará sin ciudad asignada.");
            }
        }
        
        deposito.setCiudad(ciudad);
        Deposito saved = depositoRepository.save(deposito);
        logger.info("Depósito creado exitosamente con ID: {} {}", 
            saved.getId(), 
            ciudad != null ? "(Ciudad: " + ciudad.getNombre() + ")" : "(sin ciudad)");
        return toDto(saved);
    }

    /**
     * Estandariza el nombre de una ciudad para evitar duplicados
     * - Elimina espacios al inicio y final
     * - Capitaliza cada palabra (primera letra mayúscula, resto minúsculas)
     * Ejemplos: "BUENOS AIRES" -> "Buenos Aires", "buenos aires" -> "Buenos Aires"
     * 
     * @param nombreCiudad Nombre sin estandarizar
     * @return Nombre estandarizado
     */
    private String estandarizarNombreCiudad(String nombreCiudad) {
        if (nombreCiudad == null) return null;
        
        // Trim y convertir a minúsculas
        String nombre = nombreCiudad.trim();
        if (nombre.isEmpty()) return nombre;
        
        // Capitalizar cada palabra
        String[] palabras = nombre.toLowerCase().split("\\s+");
        StringBuilder resultado = new StringBuilder();
        
        for (int i = 0; i < palabras.length; i++) {
            if (i > 0) resultado.append(" ");
            
            String palabra = palabras[i];
            if (!palabra.isEmpty()) {
                // Primera letra en mayúscula, resto en minúscula
                resultado.append(Character.toUpperCase(palabra.charAt(0)));
                if (palabra.length() > 1) {
                    resultado.append(palabra.substring(1));
                }
            }
        }
        
        return resultado.toString();
    }

    /**
     * Obtiene o crea una ciudad por nombre (ya estandarizado)
     * @param nombreCiudad Nombre de la ciudad (estandarizado)
     * @return Ciudad encontrada o creada
     */
    private Ciudad obtenerOCrearCiudadPorNombre(String nombreCiudad) {
        return ciudadRepository.findByNombreIgnoreCase(nombreCiudad)
            .orElseGet(() -> {
                logger.info("Ciudad '{}' no existe, creándola...", nombreCiudad);
                Ciudad nuevaCiudad = new Ciudad();
                nuevaCiudad.setNombre(nombreCiudad);
                Ciudad saved = ciudadRepository.save(nuevaCiudad);
                logger.info("Nueva ciudad creada: {} (ID: {})", saved.getNombre(), saved.getId());
                return saved;
            });
    }

    /**
     * Obtiene o crea una ciudad usando geocodificación inversa
     * Este método es tolerante a fallos - retorna null si no puede obtener la ciudad
     * @param latitud Latitud del depósito
     * @param longitud Longitud del depósito
     * @return Ciudad encontrada o creada, null si no se pudo determinar
     */
    private Ciudad obtenerOCrearCiudadPorCoordenadas(BigDecimal latitud, BigDecimal longitud) {
        try {
            // Consultar servicio de geocodificación
            NominatimService.UbicacionDTO ubicacion = nominatimService.obtenerUbicacion(latitud, longitud);
            
            if (ubicacion == null || ubicacion.getCiudad() == null || ubicacion.getCiudad().isEmpty()) {
                logger.warn("No se obtuvo información de ciudad desde Nominatim");
                return null;
            }
            
            String nombreCiudad = ubicacion.getCiudad();
            logger.info("Ciudad detectada: {} (Provincia: {}, País: {})", 
                nombreCiudad, ubicacion.getProvincia(), ubicacion.getPais());
            
            // Buscar si la ciudad ya existe en la base de datos
            return ciudadRepository.findByNombreIgnoreCase(nombreCiudad)
                .orElseGet(() -> {
                    // Si no existe, crearla
                    logger.info("Ciudad '{}' no existe en la base de datos, creándola...", nombreCiudad);
                    Ciudad nuevaCiudad = new Ciudad();
                    nuevaCiudad.setNombre(nombreCiudad);
                    Ciudad saved = ciudadRepository.save(nuevaCiudad);
                    logger.info("Nueva ciudad creada: {} (ID: {})", saved.getNombre(), saved.getId());
                    return saved;
                });
                
        } catch (Exception e) {
            logger.error("Error al obtener/crear ciudad para coordenadas lat={}, lon={}", latitud, longitud, e);
            return null;
        }
    }

    /**
     * Busca un depósito por su ID
     * @param id ID del depósito
     * @return DTO del depósito encontrado
     * @throws RuntimeException si no se encuentra el depósito
     */
    public DepositoDTO findById(Long id) {
        logger.info("Buscando depósito por ID: {}", id);
        Deposito deposito = depositoRepository.findById(id)
                .orElseThrow(() -> {
                    logger.warn("Depósito no encontrado con ID: {}", id);
                    return new RuntimeException("Depósito no encontrado con id: " + id);
                });
        logger.debug("Depósito encontrado: ID={}, nombre={}", deposito.getId(), deposito.getNombre());
        return toDto(deposito);
    }

    /**
     * Actualiza un depósito existente
     * @param id ID del depósito a actualizar
     * @param dto Nuevos datos del depósito
     * @return DTO del depósito actualizado
     */
    public DepositoDTO update(Long id, DepositoDTO dto) {
        logger.info("Actualizando depósito ID: {}", id);
        Deposito deposito = depositoRepository.findById(id)
                .orElseThrow(() -> {
                    logger.warn("No se pudo actualizar - Depósito no encontrado con ID: {}", id);
                    return new RuntimeException("Depósito no encontrado con id: " + id);
                });
        
        if (dto.getNombre() != null) {
            logger.debug("Actualizando nombre: {}", dto.getNombre());
            deposito.setNombre(dto.getNombre());
        }
        if (dto.getDireccion() != null) {
            logger.debug("Actualizando dirección: {}", dto.getDireccion());
            deposito.setDireccion(dto.getDireccion());
        }
        if (dto.getLatitud() != null) {
            logger.debug("Actualizando latitud: {}", dto.getLatitud());
            deposito.setLatitud(java.math.BigDecimal.valueOf(dto.getLatitud()));
        }
        if (dto.getLongitud() != null) {
            logger.debug("Actualizando longitud: {}", dto.getLongitud());
            deposito.setLongitud(java.math.BigDecimal.valueOf(dto.getLongitud()));
        }
        if (dto.getCostoEstadiaDiario() != null) {
            logger.debug("Actualizando costoEstadiaDiario: {}", dto.getCostoEstadiaDiario());
            deposito.setCostoEstadiaDiario(java.math.BigDecimal.valueOf(dto.getCostoEstadiaDiario()));
        }
        
        // Actualizar ciudad si se proporciona nombreCiudad o idCiudad
        if (dto.getNombreCiudad() != null && !dto.getNombreCiudad().trim().isEmpty()) {
            String nombreCiudad = estandarizarNombreCiudad(dto.getNombreCiudad());
            Ciudad ciudad = obtenerOCrearCiudadPorNombre(nombreCiudad);
            deposito.setCiudad(ciudad);
            logger.debug("Actualizando ciudad por nombre: {} (ID: {})", ciudad.getNombre(), ciudad.getId());
        } else if (dto.getIdCiudad() != null) {
            Ciudad ciudad = ciudadRepository.findById(dto.getIdCiudad())
                .orElseThrow(() -> new RuntimeException("Ciudad no encontrada con id: " + dto.getIdCiudad()));
            deposito.setCiudad(ciudad);
            logger.debug("Actualizando ciudad por ID: {} (ID: {})", ciudad.getNombre(), ciudad.getId());
        }
        
        Deposito saved = depositoRepository.save(deposito);
        logger.info("Depósito actualizado exitosamente: ID={}, nombre={}", saved.getId(), saved.getNombre());
        return toDto(saved);
    }

    /**
     * Convierte una entidad Deposito a su DTO
     * @param deposito Entidad depósito
     * @return DTO del depósito
     */
    private DepositoDTO toDto(Deposito deposito) {
        if (deposito == null) return null;
        DepositoDTO dto = new DepositoDTO();
        dto.setId(deposito.getId());
        dto.setNombre(deposito.getNombre());
        dto.setDireccion(deposito.getDireccion());
        if (deposito.getLatitud() != null) {
            dto.setLatitud(deposito.getLatitud().doubleValue());
        }
        if (deposito.getLongitud() != null) {
            dto.setLongitud(deposito.getLongitud().doubleValue());
        }
        if (deposito.getCostoEstadiaDiario() != null) {
            dto.setCostoEstadiaDiario(deposito.getCostoEstadiaDiario().doubleValue());
        }
        if (deposito.getCiudad() != null) {
            dto.setIdCiudad(deposito.getCiudad().getId());
            dto.setNombreCiudad(deposito.getCiudad().getNombre());
        }
        return dto;
    }

    /**
     * Convierte un DTO de Deposito a entidad
     * @param dto DTO de depósito
     * @return Entidad depósito
     */
    private Deposito toEntity(DepositoDTO dto) {
        if (dto == null) return null;
        Deposito deposito = new Deposito();
        deposito.setId(dto.getId());
        deposito.setNombre(dto.getNombre());
        deposito.setDireccion(dto.getDireccion());
        if (dto.getLatitud() != null) {
            deposito.setLatitud(java.math.BigDecimal.valueOf(dto.getLatitud()));
        }
        if (dto.getLongitud() != null) {
            deposito.setLongitud(java.math.BigDecimal.valueOf(dto.getLongitud()));
        }
        if (dto.getCostoEstadiaDiario() != null) {
            deposito.setCostoEstadiaDiario(java.math.BigDecimal.valueOf(dto.getCostoEstadiaDiario()));
        }
        return deposito;
    }
}

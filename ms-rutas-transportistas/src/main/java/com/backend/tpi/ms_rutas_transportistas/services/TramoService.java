package com.backend.tpi.ms_rutas_transportistas.services;

import com.backend.tpi.ms_rutas_transportistas.dtos.DistanciaResponseDTO;
import com.backend.tpi.ms_rutas_transportistas.dtos.TramoRequestDTO;
import com.backend.tpi.ms_rutas_transportistas.models.Ruta;
import com.backend.tpi.ms_rutas_transportistas.models.Tramo;
import com.backend.tpi.ms_rutas_transportistas.repositories.RutaRepository;
import com.backend.tpi.ms_rutas_transportistas.repositories.TramoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Optional;

/**
 * Servicio de negocio para Tramos
 * Gestiona la creación de tramos, cálculo de distancias y asignación de camiones
 * Se comunica con ms-gestion-calculos para obtener distancias
 */
@Service
public class TramoService {

    private static final Logger logger = LoggerFactory.getLogger(TramoService.class);

    @Autowired
    private TramoRepository tramoRepository;

    @Autowired
    private RutaRepository rutaRepository;

    @Autowired
    private org.springframework.web.client.RestClient calculosClient;
    
    @Autowired
    private com.backend.tpi.ms_rutas_transportistas.repositories.TipoTramoRepository tipoTramoRepository;

    @Autowired
    private com.backend.tpi.ms_rutas_transportistas.repositories.EstadoTramoRepository estadoTramoRepository;
    
    @Autowired
    private com.backend.tpi.ms_rutas_transportistas.repositories.CamionRepository camionRepository;
    
    @Autowired
    private org.springframework.web.client.RestClient solicitudesClient;

    // usamos `solicitudesClient` RestClient inyectado arriba para llamadas a ms-solicitudes

    @org.springframework.beans.factory.annotation.Value("${app.calculos.base-url:http://ms-gestion-calculos:8081}")
    private String calculosBaseUrl;

    /**
     * Crea un nuevo tramo para una ruta, calculando la distancia entre origen y destino
     * @param tramoRequestDTO Datos del tramo a crear
     * @return Tramo creado como DTO, o null si la ruta no existe
     * @throws IllegalArgumentException si los datos del tramo son inválidos
     */
    @org.springframework.transaction.annotation.Transactional
    public com.backend.tpi.ms_rutas_transportistas.dtos.TramoDTO create(TramoRequestDTO tramoRequestDTO) {
        // Validar datos de entrada
        if (tramoRequestDTO == null) {
            logger.error("TramoRequestDTO no puede ser null");
            throw new IllegalArgumentException("Los datos del tramo no pueden ser null");
        }
        if (tramoRequestDTO.getIdRuta() == null) {
            logger.error("IdRuta no puede ser null");
            throw new IllegalArgumentException("El ID de la ruta no puede ser null");
        }
        if (tramoRequestDTO.getOrigenDepositoId() == null || tramoRequestDTO.getDestinoDepositoId() == null) {
            logger.error("Depósitos origen/destino no pueden ser null");
            throw new IllegalArgumentException("Los depósitos de origen y destino son obligatorios");
        }
        if (tramoRequestDTO.getOrigenDepositoId().equals(tramoRequestDTO.getDestinoDepositoId())) {
            logger.error("Depósito origen y destino no pueden ser iguales");
            throw new IllegalArgumentException("El depósito de origen y destino deben ser diferentes");
        }
        // Validar coordenadas si están presentes
        if (tramoRequestDTO.getOrigenLat() != null && (tramoRequestDTO.getOrigenLat().compareTo(new java.math.BigDecimal("-90")) < 0 || tramoRequestDTO.getOrigenLat().compareTo(new java.math.BigDecimal("90")) > 0)) {
            logger.error("Latitud origen inválida: {}", tramoRequestDTO.getOrigenLat());
            throw new IllegalArgumentException("La latitud de origen debe estar entre -90 y 90");
        }
        if (tramoRequestDTO.getOrigenLong() != null && (tramoRequestDTO.getOrigenLong().compareTo(new java.math.BigDecimal("-180")) < 0 || tramoRequestDTO.getOrigenLong().compareTo(new java.math.BigDecimal("180")) > 0)) {
            logger.error("Longitud origen inválida: {}", tramoRequestDTO.getOrigenLong());
            throw new IllegalArgumentException("La longitud de origen debe estar entre -180 y 180");
        }
        if (tramoRequestDTO.getDestinoLat() != null && (tramoRequestDTO.getDestinoLat().compareTo(new java.math.BigDecimal("-90")) < 0 || tramoRequestDTO.getDestinoLat().compareTo(new java.math.BigDecimal("90")) > 0)) {
            logger.error("Latitud destino inválida: {}", tramoRequestDTO.getDestinoLat());
            throw new IllegalArgumentException("La latitud de destino debe estar entre -90 y 90");
        }
        if (tramoRequestDTO.getDestinoLong() != null && (tramoRequestDTO.getDestinoLong().compareTo(new java.math.BigDecimal("-180")) < 0 || tramoRequestDTO.getDestinoLong().compareTo(new java.math.BigDecimal("180")) > 0)) {
            logger.error("Longitud destino inválida: {}", tramoRequestDTO.getDestinoLong());
            throw new IllegalArgumentException("La longitud de destino debe estar entre -180 y 180");
        }
        
        logger.info("Creando nuevo tramo para ruta ID: {}", tramoRequestDTO.getIdRuta());
        Optional<Ruta> optionalRuta = rutaRepository.findById(tramoRequestDTO.getIdRuta());
        if (optionalRuta.isPresent()) {
            logger.debug("Calculando distancia del tramo entre depósito origen {} y destino {}", 
                tramoRequestDTO.getOrigenDepositoId(), tramoRequestDTO.getDestinoDepositoId());
            // call calculos service using configured base-url
        java.util.Map<String, String> distanciaReq = new java.util.HashMap<>();
        distanciaReq.put("origen", String.valueOf(tramoRequestDTO.getOrigenDepositoId()));
        distanciaReq.put("destino", String.valueOf(tramoRequestDTO.getDestinoDepositoId()));
    String token = extractBearerToken();
    ResponseEntity<DistanciaResponseDTO> distanciaRespEntity = calculosClient.post()
        .uri("/api/v1/gestion/distancia")
        .headers(h -> { if (token != null) h.setBearerAuth(token); })
        .body(distanciaReq, new org.springframework.core.ParameterizedTypeReference<java.util.Map<String,String>>() {})
        .retrieve()
        .toEntity(DistanciaResponseDTO.class);
    DistanciaResponseDTO distanciaResponse = distanciaRespEntity != null ? distanciaRespEntity.getBody() : null;

            Tramo tramo = new Tramo();
            tramo.setRuta(optionalRuta.get());
            tramo.setOrigenDepositoId(tramoRequestDTO.getOrigenDepositoId());
            tramo.setDestinoDepositoId(tramoRequestDTO.getDestinoDepositoId());
            tramo.setOrigenLat(tramoRequestDTO.getOrigenLat());
            tramo.setOrigenLong(tramoRequestDTO.getOrigenLong());
            tramo.setDestinoLat(tramoRequestDTO.getDestinoLat());
            tramo.setDestinoLong(tramoRequestDTO.getDestinoLong());
            if (distanciaResponse != null) {
                tramo.setDistancia(distanciaResponse.getDistancia());
                logger.debug("Distancia calculada para tramo: {} km", distanciaResponse.getDistancia());
            }
            // Asignar tipoTramo por defecto si existe
            try {
                tipoTramoRepository.findAll().stream().findFirst().ifPresent(tramo::setTipoTramo);
            } catch (Exception e) {
                logger.warn("No se pudo asignar tipoTramo por defecto: {}", e.getMessage());
            }
            // Asignar estado PENDIENTE por defecto si existe
            try {
                estadoTramoRepository.findByNombre("PENDIENTE").ifPresent(tramo::setEstado);
            } catch (Exception e) {
                logger.warn("No se pudo asignar estadoTramo por defecto: {}", e.getMessage());
            }
            Tramo saved = tramoRepository.save(tramo);
            logger.info("Tramo creado exitosamente con ID: {}", saved.getId());
            return toDto(saved);
        }
        logger.warn("No se pudo crear tramo - Ruta no encontrada con ID: {}", tramoRequestDTO.getIdRuta());
        return null;
    }

    /**
     * Obtiene la lista de todos los tramos registrados
     * @return Lista de tramos como DTOs
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public java.util.List<com.backend.tpi.ms_rutas_transportistas.dtos.TramoDTO> findAll() {
        return tramoRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Obtiene todos los tramos de una ruta específica
     * @param rutaId ID de la ruta
     * @return Lista de tramos de la ruta
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public java.util.List<com.backend.tpi.ms_rutas_transportistas.dtos.TramoDTO> findByRutaId(Long rutaId) {
        return tramoRepository.findByRutaId(rutaId).stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Guarda o actualiza un tramo
     * @param tramo Entidad tramo a guardar
     * @return Tramo guardado como DTO
     */
    @org.springframework.transaction.annotation.Transactional
    public com.backend.tpi.ms_rutas_transportistas.dtos.TramoDTO save(Tramo tramo) {
        Tramo saved = tramoRepository.save(tramo);
        return toDto(saved);
    }

    /**
     * Convierte una entidad Tramo a DTO
     * @param tramo Entidad a convertir
     * @return DTO con los datos del tramo
     */
    private com.backend.tpi.ms_rutas_transportistas.dtos.TramoDTO toDto(Tramo tramo) {
        if (tramo == null) return null;
        com.backend.tpi.ms_rutas_transportistas.dtos.TramoDTO dto = new com.backend.tpi.ms_rutas_transportistas.dtos.TramoDTO();
        dto.setId(tramo.getId());
        if (tramo.getRuta() != null) dto.setIdRuta(tramo.getRuta().getId());
        dto.setOrden(tramo.getOrden());
        dto.setGeneradoAutomaticamente(tramo.getGeneradoAutomaticamente());
        dto.setDuracionHoras(tramo.getDuracionHoras());
        dto.setOrigenDepositoId(tramo.getOrigenDepositoId());
        dto.setDestinoDepositoId(tramo.getDestinoDepositoId());
        dto.setOrigenLat(tramo.getOrigenLat());
        dto.setOrigenLong(tramo.getOrigenLong());
        dto.setDestinoLat(tramo.getDestinoLat());
        dto.setDestinoLong(tramo.getDestinoLong());
        dto.setDistancia(tramo.getDistancia());
        dto.setCamionDominio(tramo.getCamionDominio());
        dto.setCostoAproximado(tramo.getCostoAproximado());
        dto.setCostoReal(tramo.getCostoReal());
        dto.setFechaHoraInicioEstimada(tramo.getFechaHoraInicioEstimada());
        dto.setFechaHoraFinEstimada(tramo.getFechaHoraFinEstimada());
        dto.setFechaHoraInicioReal(tramo.getFechaHoraInicioReal());
        dto.setFechaHoraFinReal(tramo.getFechaHoraFinReal());
        return dto;
    }
    
    /**
     * Asigna un camión a un tramo específico, validando que tenga capacidad suficiente
     * @param tramoId ID del tramo
     * @param camionId ID del camión a asignar
     * @return Tramo con camión asignado como DTO, o null si el tramo no existe
     * @throws IllegalArgumentException si el camión no tiene capacidad suficiente
     */
    public com.backend.tpi.ms_rutas_transportistas.dtos.TramoDTO assignTransportista(Long tramoId, Long camionId) {
        logger.info("Asignando camión ID: {} al tramo ID: {}", camionId, tramoId);
        
        // Validar que el tramo existe
        Optional<Tramo> optionalTramo = tramoRepository.findById(tramoId);
        if (optionalTramo.isEmpty()) {
            logger.warn("No se pudo asignar camión - Tramo no encontrado con ID: {}", tramoId);
            return null;
        }
        Tramo tramo = optionalTramo.get();
        
        // Validar que el camión existe
        if (camionId == null) {
            logger.warn("No se puede asignar camión - camionId es null");
            throw new IllegalArgumentException("El ID del camión no puede ser null");
        }
        
        Optional<com.backend.tpi.ms_rutas_transportistas.models.Camion> maybeCamion = camionRepository.findById(camionId);
        if (maybeCamion.isEmpty()) {
            logger.error("Camión no encontrado con ID: {}", camionId);
            throw new IllegalArgumentException("Camión no encontrado con ID: " + camionId);
        }
        
        com.backend.tpi.ms_rutas_transportistas.models.Camion camion = maybeCamion.get();
        
        // Validar disponibilidad del camión
        if (camion.getDisponible() != null && !camion.getDisponible()) {
            logger.error("El camión {} no está disponible", camion.getDominio());
            throw new IllegalArgumentException("El camión con dominio " + camion.getDominio() + " no está disponible");
        }
        
        if (camion.getActivo() != null && !camion.getActivo()) {
            logger.error("El camión {} no está activo", camion.getDominio());
            throw new IllegalArgumentException("El camión con dominio " + camion.getDominio() + " no está activo");
        }
        
        try {
            // Obtener datos del contenedor desde la solicitud (usando DTOs)
            logger.debug("Obteniendo datos del contenedor para validar capacidad del camión");
            Ruta ruta = tramo.getRuta();
            if (ruta == null || ruta.getIdSolicitud() == null) {
                logger.error("No se puede validar capacidad - tramo sin ruta o solicitud asociada");
                throw new IllegalArgumentException("No se puede validar capacidad: tramo sin ruta o solicitud asociada");
            }

            Long solicitudId = ruta.getIdSolicitud();
            String token = extractBearerToken();

            // Consultar solicitud para obtener el contenedor (map to DTO)
            ResponseEntity<com.backend.tpi.ms_rutas_transportistas.dtos.SolicitudIntegrationDTO> solicitudEntity = solicitudesClient.get()
                    .uri("/api/v1/solicitudes/{id}", solicitudId)
                    .headers(h -> { if (token != null) h.setBearerAuth(token); })
                    .retrieve()
                    .toEntity(com.backend.tpi.ms_rutas_transportistas.dtos.SolicitudIntegrationDTO.class);

            com.backend.tpi.ms_rutas_transportistas.dtos.SolicitudIntegrationDTO solicitud = solicitudEntity != null ? solicitudEntity.getBody() : null;
            if (solicitud == null || solicitud.getContenedorId() == null) {
                logger.error("No se puede validar capacidad - solicitud sin contenedor asociado o no encontrada (id={})", solicitudId);
                throw new IllegalArgumentException("No se puede validar capacidad - solicitud sin contenedor asociado");
            }

            Long contenedorId = solicitud.getContenedorId();

            // Consultar contenedor para obtener peso y volumen (map to DTO)
            ResponseEntity<com.backend.tpi.ms_rutas_transportistas.dtos.ContenedorIntegrationDTO> contenedorEntity = solicitudesClient.get()
                    .uri("/api/v1/contenedores/{id}", contenedorId)
                    .headers(h -> { if (token != null) h.setBearerAuth(token); })
                    .retrieve()
                    .toEntity(com.backend.tpi.ms_rutas_transportistas.dtos.ContenedorIntegrationDTO.class);

            com.backend.tpi.ms_rutas_transportistas.dtos.ContenedorIntegrationDTO contenedor = contenedorEntity != null ? contenedorEntity.getBody() : null;
            if (contenedor == null) {
                logger.error("No se puede validar capacidad - contenedor no encontrado (id={})", contenedorId);
                throw new IllegalArgumentException("No se puede validar capacidad - contenedor no encontrado");
            }

            // Extraer peso y volumen del contenedor
            Double pesoCarga = contenedor.getPeso() != null ? contenedor.getPeso().doubleValue() : null;
            Double volumenCarga = contenedor.getVolumen() != null ? contenedor.getVolumen().doubleValue() : null;
            
            logger.debug("Contenedor: peso={} kg, volumen={} m³", pesoCarga, volumenCarga);
            logger.debug("Camión {}: capacidadPeso={} kg, capacidadVolumen={} m³", 
                    camion.getDominio(), camion.getCapacidadPesoMax(), camion.getCapacidadVolumenMax());
            
            // VALIDAR CAPACIDAD DE PESO
            if (pesoCarga != null && camion.getCapacidadPesoMax() != null) {
                if (pesoCarga > camion.getCapacidadPesoMax()) {
                    String mensaje = String.format(
                            "Camión insuficiente: el peso del contenedor (%.2f kg) excede la capacidad máxima del camión %s (%.2f kg)",
                            pesoCarga, camion.getDominio(), camion.getCapacidadPesoMax()
                    );
                    logger.error(mensaje);
                    throw new IllegalArgumentException(mensaje);
                }
            }
            
            // VALIDAR CAPACIDAD DE VOLUMEN
            if (volumenCarga != null && camion.getCapacidadVolumenMax() != null) {
                if (volumenCarga > camion.getCapacidadVolumenMax()) {
                    String mensaje = String.format(
                            "Camión insuficiente: el volumen del contenedor (%.2f m³) excede la capacidad máxima del camión %s (%.2f m³)",
                            volumenCarga, camion.getDominio(), camion.getCapacidadVolumenMax()
                    );
                    logger.error(mensaje);
                    throw new IllegalArgumentException(mensaje);
                }
            }
            
            logger.info("Validación de capacidad exitosa - Camión {} es compatible con la carga", camion.getDominio());
            
        } catch (IllegalArgumentException e) {
            // Re-lanzar excepciones de validación
            throw e;
        } catch (Exception e) {
            logger.error("Error al validar capacidad del camión: {}. Bloqueando asignación.", e.getMessage());
            logger.debug("Stack trace de error de validación:", e);
            // Bloquear asignación si ocurre un error al validar datos externos
            throw new RuntimeException("Error al validar capacidad del camión: " + e.getMessage(), e);
        }
        
        // Asignar camión al tramo (ya validado)
        tramo.setCamionDominio(camion.getDominio());
        
        // Marcar el camión como NO DISPONIBLE al asignarlo a un tramo
        camion.setDisponible(false);
        camionRepository.save(camion);
        logger.info("Camión {} marcado como NO DISPONIBLE", camion.getDominio());
        
        Tramo saved = tramoRepository.save(tramo);
        logger.info("Camión {} asignado exitosamente al tramo ID: {}", camion.getDominio(), tramoId);
        
        // Calcular costo aproximado ahora que el tramo tiene camión asignado
        try {
            logger.info("Calculando costo aproximado para tramo {} tras asignar camión", tramoId);
            computeAndSaveCostoAproximadoForTramo(saved);
            // Recargar el tramo para obtener el costo actualizado
            saved = tramoRepository.findById(tramoId).orElse(saved);
        } catch (Exception e) {
            logger.warn("No se pudo calcular costo aproximado para tramo {}: {}", tramoId, e.getMessage());
        }
        
        return toDto(saved);
    }
    

    /**
     * Marca el inicio de un tramo, registrando la fecha y hora actual
     * @param rutaId ID de la ruta
     * @param tramoId ID del tramo a iniciar
     * @return Tramo iniciado como DTO, o null si no existe
     * @throws RuntimeException si el tramo no pertenece a la ruta
     * @throws IllegalStateException si el tramo ya fue iniciado o no tiene camión asignado
     */
    @org.springframework.transaction.annotation.Transactional
    public com.backend.tpi.ms_rutas_transportistas.dtos.TramoDTO iniciarTramo(Long rutaId, Long tramoId, java.time.LocalDateTime fechaHoraReal) {
        logger.info("Iniciando tramo ID: {} de ruta ID: {}", tramoId, rutaId);
        Optional<Tramo> optionalTramo = tramoRepository.findById(tramoId);
        if (optionalTramo.isEmpty()) {
            logger.warn("No se pudo iniciar - Tramo no encontrado con ID: {}", tramoId);
            return null;
        }
        
        Tramo tramo = optionalTramo.get();
        if (tramo.getRuta() == null || !tramo.getRuta().getId().equals(rutaId)) {
            logger.error("El tramo ID: {} no pertenece a la ruta ID: {}", tramoId, rutaId);
            throw new RuntimeException("El tramo no pertenece a la ruta especificada");
        }
        
        // Validar que el tramo tenga un camión asignado
        if (tramo.getCamionDominio() == null || tramo.getCamionDominio().isEmpty()) {
            logger.error("El tramo ID: {} no tiene camión asignado", tramoId);
            throw new IllegalStateException("No se puede iniciar el tramo sin un camión asignado");
        }
        
        // Validar que el tramo no haya sido iniciado previamente
        if (tramo.getFechaHoraInicioReal() != null) {
            logger.error("El tramo ID: {} ya fue iniciado el {}", tramoId, tramo.getFechaHoraInicioReal());
            throw new IllegalStateException("El tramo ya fue iniciado anteriormente");
        }
        
        // VALIDAR SECUENCIA: Si no es el primer tramo, verificar que el anterior haya finalizado
        if (tramo.getOrden() != null && tramo.getOrden() > 1) {
            logger.info("Validando que el tramo anterior (orden {}) haya finalizado", tramo.getOrden() - 1);
            java.util.List<Tramo> tramosRuta = tramoRepository.findByRutaId(rutaId);
            Tramo tramoAnterior = tramosRuta.stream()
                    .filter(t -> t.getOrden() != null && t.getOrden() == tramo.getOrden() - 1)
                    .findFirst()
                    .orElse(null);
            
            if (tramoAnterior != null) {
                if (tramoAnterior.getFechaHoraFinReal() == null) {
                    logger.error("No se puede iniciar tramo ID: {} (orden {}) porque el tramo anterior ID: {} (orden {}) no ha finalizado",
                            tramoId, tramo.getOrden(), tramoAnterior.getId(), tramoAnterior.getOrden());
                    throw new IllegalStateException(String.format(
                            "No se puede iniciar este tramo. El tramo anterior (orden %d) debe finalizar primero.",
                            tramoAnterior.getOrden()));
                }
                logger.info("Validación exitosa: tramo anterior (orden {}) finalizó el {}",
                        tramoAnterior.getOrden(), tramoAnterior.getFechaHoraFinReal());
            } else {
                logger.warn("No se encontró tramo con orden {} en la ruta {}", tramo.getOrden() - 1, rutaId);
            }
        }
        
        // Usar la fecha proporcionada o la actual si no se especifica
        java.time.LocalDateTime fechaInicio = (fechaHoraReal != null) ? fechaHoraReal : java.time.LocalDateTime.now();
        tramo.setFechaHoraInicioReal(fechaInicio);
        // Actualizar estado a EN_PROCESO
        try {
            estadoTramoRepository.findByNombre("EN_PROCESO").ifPresent(tramo::setEstado);
        } catch (Exception e) {
            logger.warn("No se pudo actualizar estadoTramo a EN_PROCESO: {}", e.getMessage());
        }
        Tramo saved = tramoRepository.save(tramo);
        logger.info("Tramo ID: {} iniciado exitosamente a las {}", tramoId, saved.getFechaHoraInicioReal());
        
        // Cambiar estado de solicitud a EN_TRANSITO cuando se inicia el primer tramo
        if (tramo.getOrden() != null && tramo.getOrden() == 1) {
            Ruta ruta = tramo.getRuta();
            if (ruta != null && ruta.getIdSolicitud() != null) {
                logger.info("Primer tramo iniciado de la ruta {}, cambiando estado de solicitud {} a EN_TRANSITO",
                        ruta.getId(), ruta.getIdSolicitud());
                try {
                    String token = extractBearerToken();
                    solicitudesClient.put()
                            .uri("/api/v1/solicitudes/" + ruta.getIdSolicitud() + "/estado?nuevoEstado=EN_TRANSITO")
                            .headers(h -> { if (token != null) h.setBearerAuth(token); })
                            .retrieve()
                            .toBodilessEntity();
                    logger.info("Estado de solicitud {} cambiado a EN_TRANSITO", ruta.getIdSolicitud());
                    
                    // Actualizar estado del contenedor a EN_TRANSITO
                    updateContenedorEstado(ruta.getIdSolicitud(), "EN_TRANSITO", null, 
                        tramo.getOrigenLat(), tramo.getOrigenLong());
                } catch (Exception e) {
                    logger.error("Error al cambiar estado de solicitud a EN_TRANSITO: {}", e.getMessage());
                }
            }
        }
        
        // Actualizar estado del contenedor cuando se inicia un tramo que sale de un depósito
        if (tramo.getOrden() != null && tramo.getOrden() > 1) {
            Ruta ruta = tramo.getRuta();
            if (ruta != null && ruta.getIdSolicitud() != null && tramo.getOrigenDepositoId() != null) {
                logger.info("Tramo {} iniciado desde depósito {}, actualizando contenedor a EN_TRANSITO",
                        tramoId, tramo.getOrigenDepositoId());
                updateContenedorEstado(ruta.getIdSolicitud(), "EN_TRANSITO", 
                    null, tramo.getOrigenLat(), tramo.getOrigenLong());
            }
        }
        
        // After starting this tramo, attempt to compute and persist estadía/costo for the previous tramo
        try {
            if (saved.getRuta() != null && saved.getOrden() != null && saved.getOrden() > 1) {
                Long rutaIdAssociated = saved.getRuta().getId();
                int prevOrden = saved.getOrden() - 1;
                java.util.List<Tramo> tramosRuta = tramoRepository.findByRutaId(rutaIdAssociated);
                for (Tramo t : tramosRuta) {
                    if (t.getOrden() != null && t.getOrden() == prevOrden) {
                        try {
                            computeAndSaveCostoRealForTramo(t);
                            logger.info("Estadía calculada y costo actualizado para tramo previo ID: {}", t.getId());
                        } catch (Exception e) {
                            logger.warn("No se pudo calcular estadía para tramo previo ID {}: {}", t.getId(), e.getMessage());
                        }
                        break;
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Error intentando calcular estadía de tramo previo tras iniciar tramo {}: {}", tramoId, e.getMessage());
        }

        return toDto(saved);
    }

    /**
     * Marca la finalización de un tramo, registrando la fecha y hora
     * @param rutaId ID de la ruta
     * @param tramoId ID del tramo a finalizar
     * @param fechaHora Fecha y hora de finalización (null para usar la actual)
     * @return Tramo finalizado como DTO, o null si no existe
     * @throws RuntimeException si el tramo no pertenece a la ruta
     * @throws IllegalStateException si el tramo no fue iniciado o ya fue finalizado
     * @throws IllegalArgumentException si la fecha de fin es anterior a la de inicio
     */
    @org.springframework.transaction.annotation.Transactional
    public com.backend.tpi.ms_rutas_transportistas.dtos.TramoDTO finalizarTramo(Long rutaId, Long tramoId, java.time.LocalDateTime fechaHora) {
        logger.info("Finalizando tramo ID: {} de ruta ID: {}", tramoId, rutaId);
        Optional<Tramo> optionalTramo = tramoRepository.findById(tramoId);
        if (optionalTramo.isEmpty()) {
            logger.warn("No se pudo finalizar - Tramo no encontrado con ID: {}", tramoId);
            return null;
        }
        
        Tramo tramo = optionalTramo.get();
        if (tramo.getRuta() == null || !tramo.getRuta().getId().equals(rutaId)) {
            logger.error("El tramo ID: {} no pertenece a la ruta ID: {}", tramoId, rutaId);
            throw new RuntimeException("El tramo no pertenece a la ruta especificada");
        }
        
        // Validar que el tramo haya sido iniciado
        if (tramo.getFechaHoraInicioReal() == null) {
            logger.error("El tramo ID: {} no ha sido iniciado", tramoId);
            throw new IllegalStateException("No se puede finalizar un tramo que no ha sido iniciado");
        }
        
        // Validar que el tramo no haya sido finalizado previamente
        if (tramo.getFechaHoraFinReal() != null) {
            logger.error("El tramo ID: {} ya fue finalizado el {}", tramoId, tramo.getFechaHoraFinReal());
            throw new IllegalStateException("El tramo ya fue finalizado anteriormente");
        }
        
        // Validar que la fecha de fin sea posterior a la de inicio
        java.time.LocalDateTime fechaFin = fechaHora != null ? fechaHora : java.time.LocalDateTime.now();
        if (fechaFin.isBefore(tramo.getFechaHoraInicioReal())) {
            logger.error("La fecha de fin ({}) no puede ser anterior a la de inicio ({})", fechaFin, tramo.getFechaHoraInicioReal());
            throw new IllegalArgumentException("La fecha de finalización no puede ser anterior a la fecha de inicio");
        }
        
        tramo.setFechaHoraFinReal(fechaFin);
        // Actualizar estado a FINALIZADO
        try {
            estadoTramoRepository.findByNombre("FINALIZADO").ifPresent(tramo::setEstado);
        } catch (Exception e) {
            logger.warn("No se pudo actualizar estadoTramo a FINALIZADO: {}", e.getMessage());
        }
        
        // Liberar el camión (marcarlo como DISPONIBLE nuevamente)
        if (tramo.getCamionDominio() != null) {
            try {
                java.util.Optional<com.backend.tpi.ms_rutas_transportistas.models.Camion> maybeCamion = 
                    camionRepository.findFirstByDominio(tramo.getCamionDominio());
                if (maybeCamion.isPresent()) {
                    com.backend.tpi.ms_rutas_transportistas.models.Camion camion = maybeCamion.get();
                    camion.setDisponible(true);
                    camionRepository.save(camion);
                    logger.info("Camión {} liberado y marcado como DISPONIBLE tras finalizar tramo {}", 
                        camion.getDominio(), tramoId);
                }
            } catch (Exception e) {
                logger.warn("No se pudo liberar camión {}: {}", tramo.getCamionDominio(), e.getMessage());
            }
        }
        
        Tramo saved = tramoRepository.save(tramo);
        logger.info("Tramo ID: {} finalizado exitosamente a las {}", tramoId, saved.getFechaHoraFinReal());

        // Calcular y persistir costo real del tramo al finalizar
        try {
            computeAndSaveCostoRealForTramo(saved);
        } catch (Exception e) {
            logger.warn("No se pudo calcular costo real para tramo ID {}: {}", tramoId, e.getMessage());
        }

        // Si todos los tramos de la ruta están finalizados, calcular costo final de la ruta y notificar a solicitudes
        try {
            Long rutaIdAssociated = saved.getRuta() != null ? saved.getRuta().getId() : null;
            if (rutaIdAssociated != null) {
                java.util.List<Tramo> tramosRuta = tramoRepository.findByRutaId(rutaIdAssociated);
                boolean todosFinalizados = tramosRuta.stream().allMatch(t -> t.getFechaHoraFinReal() != null);
                
                // Actualizar estado del contenedor al finalizar el tramo
                Ruta ruta = saved.getRuta();
                if (ruta != null && ruta.getIdSolicitud() != null) {
                    // Si este tramo termina en un depósito, marcar contenedor como EN_DEPOSITO
                    if (saved.getDestinoDepositoId() != null) {
                        logger.info("Tramo {} finalizado en depósito {}, actualizando contenedor a EN_DEPOSITO",
                                tramoId, saved.getDestinoDepositoId());
                        updateContenedorEstado(ruta.getIdSolicitud(), "EN_DEPOSITO", 
                            saved.getDestinoDepositoId(), saved.getDestinoLat(), saved.getDestinoLong());
                    }
                    // Si es el último tramo (todos finalizados), marcar contenedor como ENTREGADO
                    else if (todosFinalizados) {
                        logger.info("Último tramo {} finalizado, actualizando contenedor a ENTREGADO", tramoId);
                        updateContenedorEstado(ruta.getIdSolicitud(), "ENTREGADO", 
                            null, saved.getDestinoLat(), saved.getDestinoLong());
                    }
                }
                
                if (todosFinalizados) {
                    double sumaTramos = 0.0;
                    for (Tramo t : tramosRuta) {
                        if (t.getCostoReal() != null) sumaTramos += t.getCostoReal().doubleValue();
                        else if (t.getCostoAproximado() != null) sumaTramos += t.getCostoAproximado().doubleValue();
                    }

                    // Obtener costo base de gestion
                    Double costoBaseGestionFijo = null;
                    try {
                        String token = extractBearerToken();
                        org.springframework.http.ResponseEntity<java.util.List<java.util.Map<String, Object>>> tarifasEntity = calculosClient.get()
                                .uri("/api/v1/tarifas")
                                .headers(h -> { if (token != null) h.setBearerAuth(token); })
                                .retrieve()
                                .toEntity(new org.springframework.core.ParameterizedTypeReference<java.util.List<java.util.Map<String, Object>>>() {});
                        java.util.List<java.util.Map<String, Object>> tarifas = tarifasEntity != null ? tarifasEntity.getBody() : null;
                        if (tarifas != null && !tarifas.isEmpty()) {
                            Object costoBaseObj = tarifas.get(0).get("costoBaseGestionFijo");
                            if (costoBaseObj instanceof Number) costoBaseGestionFijo = ((Number) costoBaseObj).doubleValue();
                        }
                    } catch (Exception ex) {
                        logger.warn("No se pudo obtener tarifas para cálculo final de ruta: {}", ex.getMessage());
                    }
                    if (costoBaseGestionFijo == null) costoBaseGestionFijo = 0.0;

                    double costoGestionTotal = costoBaseGestionFijo * tramosRuta.size();
                    double costoFinal = Math.round((sumaTramos + costoGestionTotal) * 100.0) / 100.0;

                    // Calcular tiempo real total (sumar duracionHoras si está disponible)
                    double tiempoRealHoras = tramosRuta.stream()
                            .mapToDouble(t -> t.getDuracionHoras() != null ? t.getDuracionHoras() : 0.0)
                            .sum();

                    // Notificar a ms-solicitudes para persistir costo final y tiempo real
                    try {
                        Long solicitudId = saved.getRuta() != null ? saved.getRuta().getIdSolicitud() : null;
                        if (solicitudId != null) {
                            String token = extractBearerToken();
                            String uri = String.format("/api/v1/solicitudes/%d/finalizar?costoFinal=%.2f&tiempoReal=%.2f",
                                    solicitudId, costoFinal, tiempoRealHoras);
                            solicitudesClient.patch()
                                    .uri(uri)
                                    .headers(h -> { if (token != null) h.setBearerAuth(token); })
                                    .retrieve()
                                    .toEntity(Object.class);
                            logger.info("Notificada ms-solicitudes: solicitud {} finalizada con costo {} y tiempo {} horas", solicitudId, costoFinal, tiempoRealHoras);
                        }
                    } catch (Exception e) {
                        logger.warn("No se pudo notificar a ms-solicitudes sobre finalización de ruta: {}", e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Error comprobando finalización de ruta para tramo ID {}: {}", tramoId, e.getMessage());
        }

        return toDto(saved);
    }

    /**
     * Asigna un camión a un tramo usando el dominio del camión
     */
    public com.backend.tpi.ms_rutas_transportistas.dtos.TramoDTO assignTransportistaByDominio(Long tramoId, String dominio) {
        logger.info("Asignando camión dominio: {} al tramo ID: {}", dominio, tramoId);

        if (dominio == null || dominio.trim().isEmpty()) {
            logger.warn("No se puede asignar camión - dominio es null o vacío");
            throw new IllegalArgumentException("El dominio del camión no puede ser null o vacío");
        }

        Optional<Tramo> optionalTramo = tramoRepository.findById(tramoId);
        if (optionalTramo.isEmpty()) {
            logger.warn("No se pudo asignar camión - Tramo no encontrado con ID: {}", tramoId);
            return null;
        }
        Tramo tramo = optionalTramo.get();

                java.util.Optional<com.backend.tpi.ms_rutas_transportistas.models.Camion> maybeCamion = camionRepository.findFirstByDominio(dominio);
        if (maybeCamion.isEmpty()) {
            logger.error("Camión no encontrado con dominio: {}", dominio);
            throw new IllegalArgumentException("Camión no encontrado con dominio: " + dominio);
        }

        com.backend.tpi.ms_rutas_transportistas.models.Camion camion = maybeCamion.get();

        // Validaciones de disponibilidad/actividad se mantienen
        if (camion.getDisponible() != null && !camion.getDisponible()) {
            logger.error("El camión {} no está disponible", camion.getDominio());
            throw new IllegalArgumentException("El camión con dominio " + camion.getDominio() + " no está disponible");
        }
        if (camion.getActivo() != null && !camion.getActivo()) {
            logger.error("El camión {} no está activo", camion.getDominio());
            throw new IllegalArgumentException("El camión con dominio " + camion.getDominio() + " no está activo");
        }

        // Reuse existing validation logic by mimicking external contenedor lookup
        try {
            // Obtener datos del contenedor desde la solicitud (usando DTOs)
            logger.debug("Obteniendo datos del contenedor para validar capacidad del camión");
            Ruta ruta = tramo.getRuta();
            if (ruta == null || ruta.getIdSolicitud() == null) {
                logger.error("No se puede validar capacidad - tramo sin ruta o solicitud asociada");
                throw new IllegalArgumentException("No se puede validar capacidad: tramo sin ruta o solicitud asociada");
            }

            Long solicitudId = ruta.getIdSolicitud();
            String token = extractBearerToken();

            ResponseEntity<com.backend.tpi.ms_rutas_transportistas.dtos.SolicitudIntegrationDTO> solicitudEntity = solicitudesClient.get()
                    .uri("/api/v1/solicitudes/{id}", solicitudId)
                    .headers(h -> { if (token != null) h.setBearerAuth(token); })
                    .retrieve()
                    .toEntity(com.backend.tpi.ms_rutas_transportistas.dtos.SolicitudIntegrationDTO.class);

            com.backend.tpi.ms_rutas_transportistas.dtos.SolicitudIntegrationDTO solicitud = solicitudEntity != null ? solicitudEntity.getBody() : null;
            if (solicitud == null || solicitud.getContenedorId() == null) {
                logger.error("No se puede validar capacidad - solicitud sin contenedor asociado o no encontrada (id={})", solicitudId);
                throw new IllegalArgumentException("No se puede validar capacidad - solicitud sin contenedor asociado");
            }

            Long contenedorId = solicitud.getContenedorId();

            ResponseEntity<com.backend.tpi.ms_rutas_transportistas.dtos.ContenedorIntegrationDTO> contenedorEntity = solicitudesClient.get()
                    .uri("/api/v1/contenedores/{id}", contenedorId)
                    .headers(h -> { if (token != null) h.setBearerAuth(token); })
                    .retrieve()
                    .toEntity(com.backend.tpi.ms_rutas_transportistas.dtos.ContenedorIntegrationDTO.class);

            com.backend.tpi.ms_rutas_transportistas.dtos.ContenedorIntegrationDTO contenedor = contenedorEntity != null ? contenedorEntity.getBody() : null;
            if (contenedor == null) {
                logger.error("No se puede validar capacidad - contenedor no encontrado (id={})", contenedorId);
                throw new IllegalArgumentException("No se puede validar capacidad - contenedor no encontrado");
            }

            Double pesoCarga = contenedor.getPeso() != null ? contenedor.getPeso().doubleValue() : null;
            Double volumenCarga = contenedor.getVolumen() != null ? contenedor.getVolumen().doubleValue() : null;

            if (pesoCarga != null && camion.getCapacidadPesoMax() != null) {
                if (pesoCarga > camion.getCapacidadPesoMax()) {
                    String mensaje = String.format(
                            "Camión insuficiente: el peso del contenedor (%.2f kg) excede la capacidad máxima del camión %s (%.2f kg)",
                            pesoCarga, camion.getDominio(), camion.getCapacidadPesoMax()
                    );
                    logger.error(mensaje);
                    throw new IllegalArgumentException(mensaje);
                }
            }
            if (volumenCarga != null && camion.getCapacidadVolumenMax() != null) {
                if (volumenCarga > camion.getCapacidadVolumenMax()) {
                    String mensaje = String.format(
                            "Camión insuficiente: el volumen del contenedor (%.2f m³) excede la capacidad máxima del camión %s (%.2f m³)",
                            volumenCarga, camion.getDominio(), camion.getCapacidadVolumenMax()
                    );
                    logger.error(mensaje);
                    throw new IllegalArgumentException(mensaje);
                }
            }

            logger.info("Validación de capacidad exitosa - Camión {} es compatible con la carga", camion.getDominio());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error al validar capacidad del camión: {}. Bloqueando asignación.", e.getMessage());
            logger.debug("Stack trace de error de validación:", e);
            throw new RuntimeException("Error al validar capacidad del camión: " + e.getMessage(), e);
        }

        tramo.setCamionDominio(camion.getDominio());
        
        // Marcar el camión como NO DISPONIBLE al asignarlo a un tramo
        camion.setDisponible(false);
        camionRepository.save(camion);
        logger.info("Camión {} marcado como NO DISPONIBLE", camion.getDominio());
        
        Tramo saved = tramoRepository.save(tramo);
        logger.info("Camión {} asignado exitosamente al tramo ID: {}", camion.getDominio(), tramoId);
        
        // Calcular costo aproximado ahora que el tramo tiene camión asignado
        try {
            logger.info("Calculando costo aproximado para tramo {} tras asignar camión", tramoId);
            computeAndSaveCostoAproximadoForTramo(saved);
            // Recargar el tramo para obtener el costo actualizado
            saved = tramoRepository.findById(tramoId).orElse(saved);
        } catch (Exception e) {
            logger.warn("No se pudo calcular costo aproximado para tramo {}: {}", tramoId, e.getMessage());
        }
        
        return toDto(saved);
    }

    /**
     * Calcula y persiste el costo real de un tramo (costo por km del camión + combustible + estadía)
     */
    private void computeAndSaveCostoRealForTramo(Tramo tramo) {
        if (tramo == null) return;

        double distancia = tramo.getDistancia() != null ? tramo.getDistancia() : 0.0;
        double costoKmCamion = 0.0;
        double costoCombustible = 0.0;
        double costoEstadia = 0.0;

        logger.info("=== Calculando costo real para tramo {} ===", tramo.getId());
        logger.info("  Distancia: {} km", distancia);
        logger.info("  Camión: {}", tramo.getCamionDominio());

        // Obtener tarifas (valor litro)
        Double valorLitro = null;
        try {
            String token = extractBearerToken();
            org.springframework.http.ResponseEntity<java.util.List<java.util.Map<String, Object>>> tarifasEntity = calculosClient.get()
                    .uri("/api/v1/tarifas")
                    .headers(h -> { if (token != null) h.setBearerAuth(token); })
                    .retrieve()
                    .toEntity(new org.springframework.core.ParameterizedTypeReference<java.util.List<java.util.Map<String, Object>>>() {});
            java.util.List<java.util.Map<String, Object>> tarifas = tarifasEntity != null ? tarifasEntity.getBody() : null;
            if (tarifas != null && !tarifas.isEmpty()) {
                Object valorLitroObj = tarifas.get(0).get("valorLitroCombustible");
                if (valorLitroObj instanceof Number) valorLitro = ((Number) valorLitroObj).doubleValue();
            }
        } catch (Exception e) {
            logger.warn("No se pudo obtener valor litro para cálculo de costo real: {}", e.getMessage());
        }
        if (valorLitro == null) valorLitro = 0.0;
        logger.info("  Valor litro combustible: ${}", valorLitro);

        // Obtener datos del camión
        if (tramo.getCamionDominio() != null && !tramo.getCamionDominio().isEmpty()) {
            try {
                java.util.Optional<com.backend.tpi.ms_rutas_transportistas.models.Camion> camionOpt = camionRepository.findFirstByDominio(tramo.getCamionDominio());
                if (camionOpt.isPresent()) {
                    com.backend.tpi.ms_rutas_transportistas.models.Camion camion = camionOpt.get();
                    logger.info("  Camión encontrado - costoPorKm: {}, consumo: {}", 
                        camion.getCostoPorKm(), camion.getConsumoCombustiblePromedio());
                    
                    if (camion.getCostoPorKm() != null) {
                        costoKmCamion = camion.getCostoPorKm() * distancia;
                        logger.info("  Costo por km: {} * {} = ${}", camion.getCostoPorKm(), distancia, costoKmCamion);
                    } else {
                        logger.warn("  Camión {} no tiene costoPorKm configurado", camion.getDominio());
                    }
                    
                    if (camion.getConsumoCombustiblePromedio() != null) {
                        double consumoLitros = camion.getConsumoCombustiblePromedio() * distancia;
                        costoCombustible = consumoLitros * valorLitro;
                        logger.info("  Costo combustible: {} L/km * {} km * ${}/L = ${}", 
                            camion.getConsumoCombustiblePromedio(), distancia, valorLitro, costoCombustible);
                    } else {
                        logger.warn("  Camión {} no tiene consumoCombustiblePromedio configurado", camion.getDominio());
                    }
                } else {
                    logger.warn("  Camión {} no encontrado en la base de datos", tramo.getCamionDominio());
                }
            } catch (Exception e) {
                logger.warn("No se pudieron obtener datos del camión {}: {}", tramo.getCamionDominio(), e.getMessage());
            }
        }

        // Calcular costo de estadía para este tramo (si corresponde)
        if (tramo.getDestinoDepositoId() != null) {
            try {
                String token = extractBearerToken();
                org.springframework.http.ResponseEntity<java.util.Map<String, Object>> depositoEntity = calculosClient.get()
                        .uri("/api/v1/depositos/{id}", tramo.getDestinoDepositoId())
                        .headers(h -> { if (token != null) h.setBearerAuth(token); })
                        .retrieve()
                        .toEntity(new org.springframework.core.ParameterizedTypeReference<java.util.Map<String, Object>>() {});
                java.util.Map<String, Object> deposito = depositoEntity != null ? depositoEntity.getBody() : null;
                Double costoEstadiaDiario = null;
                if (deposito != null) {
                    Object costoObj = deposito.get("costoEstadiaDiario");
                    if (costoObj instanceof Number) costoEstadiaDiario = ((Number) costoObj).doubleValue();
                    else if (deposito.containsKey("costo_estadia_diario") && deposito.get("costo_estadia_diario") instanceof Number)
                        costoEstadiaDiario = ((Number) deposito.get("costo_estadia_diario")).doubleValue();
                }

                if (costoEstadiaDiario != null) {
                    // Si hay siguiente tramo, usar sus fechas para calcular noches
                    Long rutaId = tramo.getRuta() != null ? tramo.getRuta().getId() : null;
                    java.time.LocalDateTime inicioSiguiente = null;
                    if (rutaId != null) {
                        java.util.List<Tramo> tramosRuta = tramoRepository.findByRutaId(rutaId);
                        tramosRuta.sort((a,b) -> java.util.Comparator.nullsLast(java.lang.Integer::compareTo).compare(a.getOrden(), b.getOrden()));
                        for (int i=0;i<tramosRuta.size();i++){
                            Tramo t = tramosRuta.get(i);
                            if (t.getId().equals(tramo.getId()) && i+1 < tramosRuta.size()) {
                                Tramo siguiente = tramosRuta.get(i+1);
                                // Only use the real start time for estadía (no fallback to estimated)
                                inicioSiguiente = siguiente.getFechaHoraInicioReal();
                                break;
                            }
                        }
                    }

                    // For real cost calculation only consider real finish and real next-start
                    java.time.LocalDateTime finActual = tramo.getFechaHoraFinReal();
                    if (finActual != null && inicioSiguiente != null) {
                        long noches = java.time.temporal.ChronoUnit.DAYS.between(finActual.toLocalDate(), inicioSiguiente.toLocalDate());
                        if (noches < 0) noches = 0;
                        costoEstadia = noches * costoEstadiaDiario;
                        logger.info("  Costo estadía: {} noches * ${} = ${}", noches, costoEstadiaDiario, costoEstadia);
                    } else {
                        logger.info("  Costo estadía no calculado - siguiente tramo no iniciado aún");
                    }
                }
            } catch (Exception e) {
                logger.warn("No se pudo obtener datos del depósito {}: {}", tramo.getDestinoDepositoId(), e.getMessage());
            }
        }

        double total = Math.round((costoKmCamion + costoCombustible + costoEstadia) * 100.0) / 100.0;
        logger.info("=== Costo total calculado: ${} (km: ${}, combustible: ${}, estadía: ${}) ===", 
            total, costoKmCamion, costoCombustible, costoEstadia);
        tramo.setCostoReal(java.math.BigDecimal.valueOf(total));
        tramoRepository.save(tramo);
    }

    /**
     * Calcula y persiste el costo aproximado de un tramo (igual al real pero usa fechas estimadas)
     */
    private void computeAndSaveCostoAproximadoForTramo(Tramo tramo) {
        if (tramo == null) return;

        double distancia = tramo.getDistancia() != null ? tramo.getDistancia() : 0.0;
        double costoKmCamion = 0.0;
        double costoCombustible = 0.0;
        double costoEstadia = 0.0;

        logger.info("=== Calculando costo aproximado para tramo {} ===", tramo.getId());
        logger.info("  Distancia: {} km", distancia);
        logger.info("  Camión: {}", tramo.getCamionDominio());

        // Obtener tarifas (valor litro)
        Double valorLitro = null;
        try {
            String token = extractBearerToken();
            org.springframework.http.ResponseEntity<java.util.List<java.util.Map<String, Object>>> tarifasEntity = calculosClient.get()
                    .uri("/api/v1/tarifas")
                    .headers(h -> { if (token != null) h.setBearerAuth(token); })
                    .retrieve()
                    .toEntity(new org.springframework.core.ParameterizedTypeReference<java.util.List<java.util.Map<String, Object>>>() {});
            java.util.List<java.util.Map<String, Object>> tarifas = tarifasEntity != null ? tarifasEntity.getBody() : null;
            if (tarifas != null && !tarifas.isEmpty()) {
                Object valorLitroObj = tarifas.get(0).get("valorLitroCombustible");
                if (valorLitroObj instanceof Number) valorLitro = ((Number) valorLitroObj).doubleValue();
            }
        } catch (Exception e) {
            logger.warn("No se pudo obtener valor litro para cálculo de costo aproximado: {}", e.getMessage());
        }
        if (valorLitro == null) valorLitro = 0.0;
        logger.info("  Valor litro combustible: ${}", valorLitro);

        // Obtener datos del camión
        if (tramo.getCamionDominio() != null && !tramo.getCamionDominio().isEmpty()) {
            try {
                java.util.Optional<com.backend.tpi.ms_rutas_transportistas.models.Camion> camionOpt = camionRepository.findFirstByDominio(tramo.getCamionDominio());
                if (camionOpt.isPresent()) {
                    com.backend.tpi.ms_rutas_transportistas.models.Camion camion = camionOpt.get();
                    logger.info("  Camión encontrado - costoPorKm: {}, consumo: {}", 
                        camion.getCostoPorKm(), camion.getConsumoCombustiblePromedio());
                    
                    if (camion.getCostoPorKm() != null) {
                        costoKmCamion = camion.getCostoPorKm() * distancia;
                        logger.info("  Costo por km: {} * {} = ${}", camion.getCostoPorKm(), distancia, costoKmCamion);
                    } else {
                        logger.warn("  Camión {} no tiene costoPorKm configurado", camion.getDominio());
                    }
                    
                    if (camion.getConsumoCombustiblePromedio() != null) {
                        double consumoLitros = camion.getConsumoCombustiblePromedio() * distancia;
                        costoCombustible = consumoLitros * valorLitro;
                        logger.info("  Costo combustible: {} L/km * {} km * ${}/L = ${}", 
                            camion.getConsumoCombustiblePromedio(), distancia, valorLitro, costoCombustible);
                    } else {
                        logger.warn("  Camión {} no tiene consumoCombustiblePromedio configurado", camion.getDominio());
                    }
                } else {
                    logger.warn("  Camión {} no encontrado en la base de datos", tramo.getCamionDominio());
                }
            } catch (Exception e) {
                logger.warn("No se pudieron obtener datos del camión {}: {}", tramo.getCamionDominio(), e.getMessage());
            }
        }

        // Calcular costo de estadía ESTIMADO para este tramo (usa fechas ESTIMADAS)
        if (tramo.getDestinoDepositoId() != null) {
            try {
                String token = extractBearerToken();
                org.springframework.http.ResponseEntity<java.util.Map<String, Object>> depositoEntity = calculosClient.get()
                        .uri("/api/v1/depositos/{id}", tramo.getDestinoDepositoId())
                        .headers(h -> { if (token != null) h.setBearerAuth(token); })
                        .retrieve()
                        .toEntity(new org.springframework.core.ParameterizedTypeReference<java.util.Map<String, Object>>() {});
                java.util.Map<String, Object> deposito = depositoEntity != null ? depositoEntity.getBody() : null;
                Double costoEstadiaDiario = null;
                if (deposito != null) {
                    Object costoObj = deposito.get("costoEstadiaDiario");
                    if (costoObj instanceof Number) costoEstadiaDiario = ((Number) costoObj).doubleValue();
                    else if (deposito.containsKey("costo_estadia_diario") && deposito.get("costo_estadia_diario") instanceof Number)
                        costoEstadiaDiario = ((Number) deposito.get("costo_estadia_diario")).doubleValue();
                }

                if (costoEstadiaDiario != null) {
                    // Usar fechas ESTIMADAS para costo aproximado
                    Long rutaId = tramo.getRuta() != null ? tramo.getRuta().getId() : null;
                    java.time.LocalDateTime inicioSiguienteEstimado = null;
                    if (rutaId != null) {
                        java.util.List<Tramo> tramosRuta = tramoRepository.findByRutaId(rutaId);
                        tramosRuta.sort((a,b) -> java.util.Comparator.nullsLast(java.lang.Integer::compareTo).compare(a.getOrden(), b.getOrden()));
                        for (int i=0;i<tramosRuta.size();i++){
                            Tramo t = tramosRuta.get(i);
                            if (t.getId().equals(tramo.getId()) && i+1 < tramosRuta.size()) {
                                Tramo siguiente = tramosRuta.get(i+1);
                                inicioSiguienteEstimado = siguiente.getFechaHoraInicioEstimada();
                                break;
                            }
                        }
                    }

                    java.time.LocalDateTime finEstimado = tramo.getFechaHoraFinEstimada();
                    if (finEstimado != null && inicioSiguienteEstimado != null) {
                        long noches = java.time.temporal.ChronoUnit.DAYS.between(finEstimado.toLocalDate(), inicioSiguienteEstimado.toLocalDate());
                        if (noches < 0) noches = 0;
                        costoEstadia = noches * costoEstadiaDiario;
                        logger.info("  Costo estadía estimado: {} noches * ${} = ${}", noches, costoEstadiaDiario, costoEstadia);
                    } else {
                        logger.info("  Costo estadía no calculado - fechas estimadas no disponibles");
                    }
                }
            } catch (Exception e) {
                logger.warn("No se pudo obtener datos del depósito {}: {}", tramo.getDestinoDepositoId(), e.getMessage());
            }
        }

        double total = Math.round((costoKmCamion + costoCombustible + costoEstadia) * 100.0) / 100.0;
        logger.info("=== Costo aproximado calculado: ${} (km: ${}, combustible: ${}, estadía: ${}) ===", 
            total, costoKmCamion, costoCombustible, costoEstadia);
        tramo.setCostoAproximado(java.math.BigDecimal.valueOf(total));
        tramoRepository.save(tramo);
    }

    /**
     * Calcula el número de noches entre el fin de un tramo y el inicio del siguiente.
     * Reutiliza la misma lógica que se usa internamente en el cálculo de costo real.
     * @param actual Tramo actual
     * @param siguiente Tramo siguiente
     * @return noches >= 0
     */
    public long calculateNightsBetween(Tramo actual, Tramo siguiente) {
        if (actual == null || siguiente == null) return 0L;
        // For real-night calculation require real timestamps only
        java.time.LocalDateTime finActual = actual.getFechaHoraFinReal();
        java.time.LocalDateTime inicioSiguiente = siguiente.getFechaHoraInicioReal();
        if (finActual == null || inicioSiguiente == null) return 0L;
        long noches = java.time.temporal.ChronoUnit.DAYS.between(finActual.toLocalDate(), inicioSiguiente.toLocalDate());
        return Math.max(0L, noches);
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

    /**
     * Elimina un tramo por su ID
     * @param id ID del tramo a eliminar
     */
    @org.springframework.transaction.annotation.Transactional
    public void delete(Long id) {
        logger.info("Eliminando tramo con ID: {}", id);
        tramoRepository.deleteById(id);
    }

    /**
    * Actualiza la fecha de llegada de un tramo
    * Si es el último tramo, cambia el estado de la solicitud a COMPLETADA
     * @param tramoId ID del tramo
     * @param fechaLlegada Fecha y hora de llegada
     * @return Tramo actualizado
     */
    @org.springframework.transaction.annotation.Transactional
    public com.backend.tpi.ms_rutas_transportistas.dtos.TramoDTO updateFechaLlegada(Long tramoId, java.time.LocalDateTime fechaLlegada) {
        logger.info("Actualizando fecha de llegada del tramo {}: {}", tramoId, fechaLlegada);
        Tramo tramo = tramoRepository.findById(tramoId)
                .orElseThrow(() -> new RuntimeException("Tramo no encontrado con ID: " + tramoId));
        
        tramo.setFechaHoraFinReal(fechaLlegada);
        
        // Cambiar estado del tramo a COMPLETADO
        estadoTramoRepository.findByNombre("COMPLETADO").ifPresent(tramo::setEstado);
        
        // Liberar el camión (marcarlo como DISPONIBLE nuevamente)
        if (tramo.getCamionDominio() != null) {
            try {
                java.util.Optional<com.backend.tpi.ms_rutas_transportistas.models.Camion> maybeCamion = 
                    camionRepository.findFirstByDominio(tramo.getCamionDominio());
                if (maybeCamion.isPresent()) {
                    com.backend.tpi.ms_rutas_transportistas.models.Camion camion = maybeCamion.get();
                    camion.setDisponible(true);
                    camionRepository.save(camion);
                    logger.info("Camión {} liberado y marcado como DISPONIBLE tras completar tramo {}", 
                        camion.getDominio(), tramoId);
                }
            } catch (Exception e) {
                logger.warn("No se pudo liberar camión {}: {}", tramo.getCamionDominio(), e.getMessage());
            }
        }
        
        tramo = tramoRepository.save(tramo);
        logger.info("Fecha de llegada actualizada para tramo ID: {} y estado cambiado a COMPLETADO", tramoId);
        
        // Calcular y persistir costo real del tramo al completar
        try {
            computeAndSaveCostoRealForTramo(tramo);
        } catch (Exception e) {
            logger.warn("No se pudo calcular costo real para tramo ID {}: {}", tramoId, e.getMessage());
        }
        
        // Verificar si es el último tramo de la ruta para cambiar estado de solicitud a ENTREGADO
        Ruta ruta = tramo.getRuta();
        if (ruta != null && ruta.getIdSolicitud() != null) {
            java.util.List<Tramo> tramosRuta = tramoRepository.findByRutaId(ruta.getId());
            boolean todosCompletados = tramosRuta.stream()
                    .allMatch(t -> t.getFechaHoraFinReal() != null);

            if (todosCompletados) {
                logger.info("Todos los tramos de la ruta {} completados, cambiando estado de solicitud {} a COMPLETADA", ruta.getId(), ruta.getIdSolicitud());
                    try {
                        String token = extractBearerToken();
                        solicitudesClient.put()
                                .uri("/api/v1/solicitudes/" + ruta.getIdSolicitud() + "/estado?nuevoEstado=COMPLETADA")
                                .headers(h -> { if (token != null) h.setBearerAuth(token); })
                                .retrieve()
                                .toBodilessEntity();
                        logger.info("Estado de solicitud {} cambiado a COMPLETADA", ruta.getIdSolicitud());
                    } catch (Exception e) {
                        logger.error("Error al cambiar estado de solicitud a COMPLETADA: {}", e.getMessage());
                    }
            }
        }
        
        return toDto(tramo);
    }

    /**
     * Actualiza el estado de un contenedor asociado a una solicitud.
     * Este método consulta la solicitud para obtener el ID del contenedor,
     * y luego actualiza su estado a través del microservicio ms-solicitudes.
     * 
     * @param solicitudId ID de la solicitud asociada al contenedor
     * @param nuevoEstado Nuevo estado del contenedor (ej: "EN_TRANSITO", "EN_DEPOSITO", "ENTREGADO")
     * @param depositoId ID del depósito donde se encuentra (null si está en tránsito)
     * @param lat Latitud de la ubicación actual (opcional)
     * @param lng Longitud de la ubicación actual (opcional)
     */
    private void updateContenedorEstado(Long solicitudId, String nuevoEstado, Long depositoId, 
                                        java.math.BigDecimal lat, java.math.BigDecimal lng) {
        try {
            // 1. Obtener la solicitud para encontrar el contenedorId
            String token = extractBearerToken();
            ResponseEntity<com.backend.tpi.ms_rutas_transportistas.dtos.SolicitudIntegrationDTO> solicitudEntity = 
                solicitudesClient.get()
                    .uri("/api/v1/solicitudes/" + solicitudId)
                    .headers(h -> { if (token != null) h.setBearerAuth(token); })
                    .retrieve()
                    .toEntity(com.backend.tpi.ms_rutas_transportistas.dtos.SolicitudIntegrationDTO.class);
            
            com.backend.tpi.ms_rutas_transportistas.dtos.SolicitudIntegrationDTO solicitud = solicitudEntity.getBody();
            
            if (solicitud == null || solicitud.getContenedorId() == null) {
                logger.warn("No se puede actualizar contenedor: solicitud {} no tiene contenedor asignado", solicitudId);
                return;
            }
            
            Long contenedorId = solicitud.getContenedorId();
            
            // 2. Actualizar estado del contenedor usando PATCH endpoint
            String uri = String.format("/api/v1/contenedores/%d?estadoNombre=%s", contenedorId, nuevoEstado);
            
            solicitudesClient.patch()
                .uri(uri)
                .headers(h -> { if (token != null) h.setBearerAuth(token); })
                .retrieve()
                .toBodilessEntity();
            
            logger.info("Contenedor {} actualizado a estado: {} (solicitud: {}, depósito: {}, ubicación: {}, {})", 
                contenedorId, nuevoEstado, solicitudId, depositoId, lat, lng);
            
        } catch (Exception e) {
            logger.error("Error al actualizar estado del contenedor para solicitud {}: {}", 
                solicitudId, e.getMessage());
        }
    }
}

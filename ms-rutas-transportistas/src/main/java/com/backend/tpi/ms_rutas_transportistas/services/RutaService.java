package com.backend.tpi.ms_rutas_transportistas.services;

import com.backend.tpi.ms_rutas_transportistas.dtos.CreateRutaDTO;
import com.backend.tpi.ms_rutas_transportistas.dtos.RutaDTO;
import com.backend.tpi.ms_rutas_transportistas.dtos.RutaTentativaDTO;
import com.backend.tpi.ms_rutas_transportistas.dtos.TramoDTO;
import com.backend.tpi.ms_rutas_transportistas.dtos.TramoTentativoDTO;
import com.backend.tpi.ms_rutas_transportistas.models.Ruta;
import com.backend.tpi.ms_rutas_transportistas.models.Tramo;
import com.backend.tpi.ms_rutas_transportistas.repositories.RutaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Servicio de negocio para Rutas
 * Gestiona la creación de rutas y la asignación de transportistas a tramos
 */
@Service
public class RutaService {

    private static final Logger logger = LoggerFactory.getLogger(RutaService.class);

    @Autowired
    private RutaRepository rutaRepository;
    
    @Autowired
    private TramoService tramoService;
    
    @Autowired
    private RutaTentativaService rutaTentativaService;
    
    @Autowired
    private OSRMService osrmService;
    
    @Autowired
    private com.backend.tpi.ms_rutas_transportistas.repositories.TramoRepository tramoRepository;
    
    @Autowired
    private com.backend.tpi.ms_rutas_transportistas.repositories.CamionRepository camionRepository;
    
    @Autowired
    private org.springframework.web.client.RestClient calculosClient;
    
    @Autowired
    private org.springframework.web.client.RestClient solicitudesClient;

    @Autowired
    private com.backend.tpi.ms_rutas_transportistas.repositories.TipoTramoRepository tipoTramoRepository;

    @Autowired
    private com.backend.tpi.ms_rutas_transportistas.repositories.EstadoTramoRepository estadoTramoRepository;

    @Autowired
    private DepositoService depositoService;

    
    @org.springframework.beans.factory.annotation.Value("${app.solicitudes.base-url:http://ms-solicitudes:8080}")
    private String solicitudesBaseUrl;

    @org.springframework.beans.factory.annotation.Value("${app.rutas.estadia-deposito-horas:24.0}")
    private double estadiaDepositoHoras;

    // Manual mapping - ModelMapper removed

    /**
     * Crea una nueva ruta para una solicitud
     * Si se proporcionan IDs de depósitos (origen, destino y opcionalmente intermedios),
     * automáticamente calcula la ruta tentativa y crea los tramos correspondientes.
     * 
     * @param createRutaDTO Datos de la ruta a crear (incluye idSolicitud y datos de depósitos)
     * @return DTO de la ruta creada
     * @throws IllegalArgumentException si los datos son inválidos o ya existe una ruta para la solicitud
     */
    @org.springframework.transaction.annotation.Transactional
    public RutaDTO create(CreateRutaDTO createRutaDTO) {
        // Validar datos de entrada
        if (createRutaDTO == null) {
            logger.error("CreateRutaDTO no puede ser null");
            throw new IllegalArgumentException("Los datos de la ruta no pueden ser null");
        }
        if (createRutaDTO.getIdSolicitud() == null) {
            logger.error("IdSolicitud no puede ser null");
            throw new IllegalArgumentException("El ID de la solicitud es obligatorio");
        }

        logger.debug("Creando nueva ruta para solicitud ID: {}", createRutaDTO.getIdSolicitud());
        Ruta ruta = new Ruta();
        ruta.setIdSolicitud(createRutaDTO.getIdSolicitud());
        ruta = rutaRepository.save(ruta);
        logger.info("Ruta creada exitosamente con ID: {} para solicitud ID: {}", ruta.getId(), createRutaDTO.getIdSolicitud());
        
        // Si se proporcionaron depósitos, calcular ruta tentativa y crear tramos automáticamente
        if (createRutaDTO.getOrigenDepositoId() != null && createRutaDTO.getDestinoDepositoId() != null) {
            logger.info("Depósitos especificados - calculando ruta tentativa automáticamente");
            try {
                // Calcular la mejor ruta (con o sin variantes)
                boolean calcularVariantes = createRutaDTO.getCalcularRutaOptima() != null && 
                                           createRutaDTO.getCalcularRutaOptima();
                
                RutaTentativaDTO rutaTentativa = rutaTentativaService.calcularMejorRuta(
                        createRutaDTO.getOrigenDepositoId(),
                        createRutaDTO.getDestinoDepositoId(),
                        createRutaDTO.getDepositosIntermediosIds(),
                        calcularVariantes
                );
                
                if (rutaTentativa.getExitoso() && rutaTentativa.getTramos() != null) {
                    logger.info("Ruta tentativa calculada: {} km, {} tramos - creando tramos automáticamente",
                            rutaTentativa.getDistanciaTotal(), rutaTentativa.getNumeroTramos());
                    
                    // Crear tramos basados en la ruta calculada
                    for (TramoTentativoDTO tramoTentativo : rutaTentativa.getTramos()) {
                        Tramo tramo = new Tramo();
                        tramo.setRuta(ruta);
                        tramo.setOrden(tramoTentativo.getOrden());
                        tramo.setOrigenDepositoId(tramoTentativo.getOrigenDepositoId());
                        tramo.setDestinoDepositoId(tramoTentativo.getDestinoDepositoId());
                        tramo.setDistancia(tramoTentativo.getDistanciaKm());
                        tramo.setDuracionHoras(tramoTentativo.getDuracionHoras());
                        tramo.setGeneradoAutomaticamente(true); // Marcar como generado automáticamente
                        
                        // Buscar y asignar estado PENDIENTE
                        // Por ahora dejamos estado null - debería buscarse de la BD
                        
                        tramoService.save(tramo);
                        logger.debug("Tramo {} creado: {} -> {} ({} km)", 
                                tramo.getOrden(),
                                tramoTentativo.getOrigenDepositoNombre(),
                                tramoTentativo.getDestinoDepositoNombre(),
                                tramoTentativo.getDistanciaKm());
                    }
                    
                    logger.info("Creados {} tramos automáticamente para la ruta ID: {}", 
                            rutaTentativa.getNumeroTramos(), ruta.getId());
                } else {
                    logger.warn("No se pudo calcular ruta tentativa: {}", rutaTentativa.getMensaje());
                }
            } catch (Exception e) {
                logger.error("Error al calcular y crear tramos automáticos: {}", e.getMessage(), e);
                // No lanzamos excepción - la ruta se creó exitosamente, solo falló la creación de tramos
            }
        } else {
            logger.info("No se especificaron depósitos - ruta creada sin tramos automáticos");
        }
        
        // Intentar notificar al microservicio de solicitudes para asociar la ruta creada a la solicitud
        try {
            String token = extractBearerToken();
            solicitudesClient.patch()
                    .uri("/api/v1/solicitudes/" + ruta.getIdSolicitud() + "/ruta?rutaId=" + ruta.getId())
                    .headers(h -> { if (token != null) h.setBearerAuth(token); })
                    .retrieve()
                    .toEntity(Object.class);
            logger.info("Notificada solicitud {} con rutaId {}", ruta.getIdSolicitud(), ruta.getId());
        } catch (Exception e) {
            logger.warn("No se pudo notificar a ms-solicitudes para solicitud {}: {}", ruta.getIdSolicitud(), e.getMessage());
        }

        return toDto(ruta);
    }

    /**
     * Calcula el número de noches entre el fin de un tramo y el inicio del siguiente.
     * Se usa para computar el costo de estadía en el depósito destino del tramo actual.
     * @param actual Tramo actual (se toma su fecha de fin real o estimada)
     * @param siguiente Tramo siguiente (se toma su fecha de inicio real o estimada)
     * @return número de noches (>= 0)
     */
    // Delegated to TramoService.calculateNightsBetween

    /**
     * Obtiene todas las rutas del sistema
     * @return Lista de DTOs de rutas
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<RutaDTO> findAll() {
        logger.debug("Buscando todas las rutas");
        List<RutaDTO> rutas = rutaRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        logger.debug("Encontradas {} rutas", rutas.size());
        return rutas;
    }

    /**
     * Busca una ruta por su ID
     * @param id ID de la ruta
     * @return DTO de la ruta encontrada, o null si no existe
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public RutaDTO findById(Long id) {
        logger.debug("Buscando ruta por ID: {}", id);
        Optional<Ruta> ruta = rutaRepository.findById(id);
        if (ruta.isPresent()) {
            logger.debug("Ruta encontrada con ID: {}", id);
        } else {
            logger.warn("Ruta no encontrada con ID: {}", id);
        }
        return ruta.map(this::toDto).orElse(null);
    }

    /**
     * Elimina una ruta por su ID
     * @param id ID de la ruta a eliminar
     * @throws RuntimeException si la ruta tiene tramos asignados
     */
    @org.springframework.transaction.annotation.Transactional
    public void delete(Long id) {
        logger.info("Eliminando ruta ID: {}", id);
        
        // Validar que no tenga tramos asignados
        long cantidadTramos = tramoRepository.countByRutaId(id);
        if (cantidadTramos > 0) {
            throw new RuntimeException("No se puede eliminar la ruta ID " + id + 
                " porque tiene " + cantidadTramos + " tramo(s) asignado(s). " +
                "Debe eliminar primero los tramos asociados.");
        }
        
        rutaRepository.deleteById(id);
        logger.debug("Ruta ID: {} eliminada de la base de datos", id);
    }

    /**
     * Convierte una entidad Ruta a su DTO
     * @param ruta Entidad ruta
     * @return DTO de la ruta
     */
    private RutaDTO toDto(Ruta ruta) {
        if (ruta == null) return null;
        RutaDTO dto = new RutaDTO();
        dto.setId(ruta.getId());
        dto.setIdSolicitud(ruta.getIdSolicitud());
        dto.setFechaCreacion(ruta.getFechaCreacion());
        dto.setOpcionSeleccionadaId(ruta.getOpcionSeleccionadaId());
        
        // Incluir los tramos de la ruta
        List<TramoDTO> tramosDTO = tramoService.findByRutaId(ruta.getId());
        dto.setTramos(tramosDTO);
        
        return dto;
    }

    // ----- Integration/stub methods -----
    /**
     * Asigna un transportista (camión) a una ruta
     * Busca el primer tramo sin asignar y le asigna el transportista
     * @param rutaId ID de la ruta
     * @param transportistaId ID del camión a asignar
     * @return DTO del tramo con el transportista asignado
     * @throws IllegalArgumentException si la ruta no existe o el transportistaId es null
     */
    @org.springframework.transaction.annotation.Transactional
    public Object assignTransportista(Long rutaId, Long transportistaId) {
        // Validar que transportistaId no sea null
        if (transportistaId == null) {
            logger.error("TransportistaId no puede ser null");
            throw new IllegalArgumentException("El ID del transportista no puede ser null");
        }
        
        logger.info("Asignando transportista ID: {} a ruta ID: {}", transportistaId, rutaId);
        Optional<Ruta> optionalRuta = rutaRepository.findById(rutaId);
        if (optionalRuta.isEmpty()) {
            logger.error("No se puede asignar transportista - Ruta no encontrada con ID: {}", rutaId);
            throw new IllegalArgumentException("Ruta not found: " + rutaId);
        }
        // Find first unassigned tramo for this ruta and delegate assignment to TramoService
        logger.debug("Buscando tramos sin asignar para ruta ID: {}", rutaId);
        java.util.List<com.backend.tpi.ms_rutas_transportistas.dtos.TramoDTO> tramos = tramoService.findByRutaId(rutaId);
        if (tramos == null || tramos.isEmpty()) {
            logger.warn("No se encontraron tramos para ruta ID: {}", rutaId);
            Map<String, Object> result = new HashMap<>();
            result.put("rutaId", rutaId);
            result.put("status", "no_tramos");
            return result;
        }
        for (com.backend.tpi.ms_rutas_transportistas.dtos.TramoDTO t : tramos) {
            if (t.getCamionDominio() == null || t.getCamionDominio().isEmpty()) {
                logger.debug("Tramo sin asignar encontrado - ID: {}, asignando transportista", t.getId());
                // delegate to TramoService to assign the camion and persist
                com.backend.tpi.ms_rutas_transportistas.dtos.TramoDTO assigned = tramoService.assignTransportista(t.getId(), transportistaId);
                logger.info("Transportista ID: {} asignado exitosamente a ruta ID: {}", transportistaId, rutaId);
                return assigned != null ? assigned : java.util.Collections.emptyMap();
            }
        }
        logger.info("Todos los tramos de la ruta ID: {} ya tienen transportista asignado", rutaId);
        Map<String, Object> result = new HashMap<>();
        result.put("rutaId", rutaId);
        result.put("status", "all_tramos_assigned");
        return result;
    }

    /**
     * Busca una ruta por el ID de la solicitud asociada
     * @param solicitudId ID de la solicitud
     * @return DTO de la ruta encontrada, o null si no existe
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Object findBySolicitudId(Long solicitudId) {
        logger.debug("Buscando ruta por solicitud ID: {}", solicitudId);
        Optional<Ruta> ruta = rutaRepository.findByIdSolicitud(solicitudId);
        if (ruta.isPresent()) {
            logger.debug("Ruta encontrada para solicitud ID: {} - ruta ID: {}", solicitudId, ruta.get().getId());
        } else {
            logger.warn("No se encontró ruta para solicitud ID: {}", solicitudId);
        }
        return ruta.map(this::toDto).orElse(null);
    }

    /**
     * Calcula las distancias y duraciones de todos los tramos de una ruta usando OSRM con ruta múltiple
     * Este método busca todos los tramos ordenados de la ruta, extrae sus coordenadas,
     * llama al endpoint de ruta múltiple de OSRM para calcular la ruta óptima completa,
     * y actualiza cada tramo con las distancias y duraciones calculadas.
     * 
     * @param rutaId ID de la ruta
     * @return Map con información de la ruta calculada (distanciaTotal, duracionTotal, tramosActualizados)
     * @throws IllegalArgumentException si la ruta no existe o no tiene tramos
     */
    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> calcularRutaCompleta(Long rutaId) {
        logger.info("Calculando ruta completa para ruta ID: {}", rutaId);
        
        // Verificar que la ruta existe
        Optional<Ruta> optionalRuta = rutaRepository.findById(rutaId);
        if (optionalRuta.isEmpty()) {
            logger.error("No se puede calcular - Ruta no encontrada con ID: {}", rutaId);
            throw new IllegalArgumentException("Ruta no encontrada con ID: " + rutaId);
        }
        
        // Buscar todos los tramos de la ruta ordenados
        List<Tramo> tramos = tramoRepository.findByRutaId(rutaId);
        if (tramos == null || tramos.isEmpty()) {
            logger.error("No se puede calcular - Ruta ID: {} no tiene tramos", rutaId);
            throw new IllegalArgumentException("La ruta no tiene tramos para calcular");
        }
        
        // Ordenar tramos por número de orden
        tramos.sort((t1, t2) -> {
            Integer orden1 = t1.getOrden() != null ? t1.getOrden() : Integer.MAX_VALUE;
            Integer orden2 = t2.getOrden() != null ? t2.getOrden() : Integer.MAX_VALUE;
            return orden1.compareTo(orden2);
        });
        
        logger.debug("Encontrados {} tramos para la ruta ID: {}", tramos.size(), rutaId);
        
        // Extraer coordenadas de todos los puntos (origen del primer tramo + destino de cada tramo)
        List<com.backend.tpi.ms_rutas_transportistas.dtos.osrm.CoordenadaDTO> coordenadas = new java.util.ArrayList<>();
        
        // Agregar origen del primer tramo
        Tramo primerTramo = tramos.get(0);
        if (primerTramo.getOrigenLat() != null && primerTramo.getOrigenLong() != null) {
            coordenadas.add(new com.backend.tpi.ms_rutas_transportistas.dtos.osrm.CoordenadaDTO(
                primerTramo.getOrigenLat().doubleValue(),
                primerTramo.getOrigenLong().doubleValue()
            ));
        } else {
            logger.error("Primer tramo (orden {}) no tiene coordenadas de origen", primerTramo.getOrden());
            throw new IllegalArgumentException("El primer tramo no tiene coordenadas de origen");
        }
        
        // Agregar destino de cada tramo
        for (Tramo tramo : tramos) {
            if (tramo.getDestinoLat() != null && tramo.getDestinoLong() != null) {
                coordenadas.add(new com.backend.tpi.ms_rutas_transportistas.dtos.osrm.CoordenadaDTO(
                    tramo.getDestinoLat().doubleValue(),
                    tramo.getDestinoLong().doubleValue()
                ));
            } else {
                logger.error("Tramo (orden {}) no tiene coordenadas de destino", tramo.getOrden());
                throw new IllegalArgumentException("El tramo con orden " + tramo.getOrden() + " no tiene coordenadas de destino");
            }
        }
        
        logger.debug("Calculando ruta múltiple con {} puntos de coordenadas", coordenadas.size());
        
        // Llamar a OSRM para calcular la ruta completa
        com.backend.tpi.ms_rutas_transportistas.dtos.osrm.RutaCalculadaDTO rutaCalculada = 
            osrmService.calcularRutaMultiple(coordenadas.toArray(new com.backend.tpi.ms_rutas_transportistas.dtos.osrm.CoordenadaDTO[0]));
        
        if (!rutaCalculada.isExitoso()) {
            logger.error("Error al calcular ruta múltiple: {}", rutaCalculada.getMensaje());
            throw new RuntimeException("Error al calcular ruta con OSRM: " + rutaCalculada.getMensaje());
        }
        
        logger.info("Ruta múltiple calculada exitosamente: {} km, {} horas", 
            rutaCalculada.getDistanciaKm(), rutaCalculada.getDuracionHoras());
        
        // Ahora calcular distancias individuales de cada tramo
        double distanciaTotal = 0.0;
        double duracionTotal = 0.0;
        int tramosActualizados = 0;
        
        for (int i = 0; i < tramos.size(); i++) {
            Tramo tramo = tramos.get(i);
            
            // Calcular distancia y duración de este tramo específico (desde su origen a su destino)
            com.backend.tpi.ms_rutas_transportistas.dtos.osrm.CoordenadaDTO origen = new com.backend.tpi.ms_rutas_transportistas.dtos.osrm.CoordenadaDTO(
                tramo.getOrigenLat().doubleValue(),
                tramo.getOrigenLong().doubleValue()
            );
            com.backend.tpi.ms_rutas_transportistas.dtos.osrm.CoordenadaDTO destino = new com.backend.tpi.ms_rutas_transportistas.dtos.osrm.CoordenadaDTO(
                tramo.getDestinoLat().doubleValue(),
                tramo.getDestinoLong().doubleValue()
            );
            
            com.backend.tpi.ms_rutas_transportistas.dtos.osrm.RutaCalculadaDTO rutaTramo = 
                osrmService.calcularRuta(origen, destino);
            
            if (rutaTramo.isExitoso()) {
                // Actualizar distancia y duración del tramo
                tramo.setDistancia(rutaTramo.getDistanciaKm());
                tramo.setDuracionHoras(rutaTramo.getDuracionHoras());
                tramoRepository.save(tramo);
                
                distanciaTotal += rutaTramo.getDistanciaKm();
                duracionTotal += rutaTramo.getDuracionHoras();
                tramosActualizados++;
                
                logger.debug("Tramo {} actualizado: {} km, {} horas", 
                    tramo.getOrden(), rutaTramo.getDistanciaKm(), rutaTramo.getDuracionHoras());
            } else {
                logger.warn("No se pudo calcular distancia del tramo {}: {}", 
                    tramo.getOrden(), rutaTramo.getMensaje());
            }
        }
        
        // Preparar respuesta
        Map<String, Object> resultado = new HashMap<>();
        resultado.put("rutaId", rutaId);
        resultado.put("distanciaTotal", Math.round(distanciaTotal * 100.0) / 100.0);
        resultado.put("duracionTotalHoras", Math.round(duracionTotal * 100.0) / 100.0);
        resultado.put("duracionTotalMinutos", Math.round(duracionTotal * 60.0 * 100.0) / 100.0);
        resultado.put("numeroTramos", tramos.size());
        resultado.put("tramosActualizados", tramosActualizados);
        resultado.put("exitoso", tramosActualizados == tramos.size());
        resultado.put("mensaje", String.format("Ruta calculada: %d/%d tramos actualizados", tramosActualizados, tramos.size()));
        
        logger.info("Cálculo de ruta completa finalizado - Distancia total: {} km, Duración: {} horas, Tramos actualizados: {}/{}",
            resultado.get("distanciaTotal"), resultado.get("duracionTotalHoras"), tramosActualizados, tramos.size());
        
        return resultado;
    }

    /**
     * Calcula el costo total de una ruta sumando los costos de todos sus tramos
     * Obtiene la tarifa por km desde el microservicio de cálculos y calcula el costo
     * de cada tramo en base a su distancia.
     * 
     * @param rutaId ID de la ruta
     * @return Map con información de costos (costoTotal, costosPorTramo, tarifaPorKm)
     * @throws IllegalArgumentException si la ruta no existe o no tiene tramos
     */
    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> calcularCostoRuta(Long rutaId) {
        logger.info("Calculando costo total para ruta ID: {}", rutaId);
        
        // Verificar que la ruta existe
        Optional<Ruta> optionalRuta = rutaRepository.findById(rutaId);
        if (optionalRuta.isEmpty()) {
            logger.error("No se puede calcular costo - Ruta no encontrada con ID: {}", rutaId);
            throw new IllegalArgumentException("Ruta no encontrada con ID: " + rutaId);
        }
        
        // Buscar todos los tramos de la ruta
        List<Tramo> tramos = tramoRepository.findByRutaId(rutaId);
        if (tramos == null || tramos.isEmpty()) {
            logger.error("No se puede calcular costo - Ruta ID: {} no tiene tramos", rutaId);
            throw new IllegalArgumentException("La ruta no tiene tramos para calcular el costo");
        }

        // Ordenar por orden
        tramos.sort((t1, t2) -> {
            Integer orden1 = t1.getOrden() != null ? t1.getOrden() : Integer.MAX_VALUE;
            Integer orden2 = t2.getOrden() != null ? t2.getOrden() : Integer.MAX_VALUE;
            return orden1.compareTo(orden2);
        });

        logger.debug("Encontrados {} tramos para calcular costo", tramos.size());

        // Obtener tarifa (costo base de gestión y valor litro combustible) desde el servicio de cálculos
        Double valorLitro = null;
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
                Object valorLitroObj = tarifas.get(0).get("valorLitroCombustible");
                Object costoBaseObj = tarifas.get(0).get("costoBaseGestionFijo");
                if (valorLitroObj instanceof Number) valorLitro = ((Number) valorLitroObj).doubleValue();
                if (costoBaseObj instanceof Number) costoBaseGestionFijo = ((Number) costoBaseObj).doubleValue();
            }
        } catch (Exception e) {
            logger.warn("No se pudo obtener tarifas desde ms-gestion-calculos: {}", e.getMessage());
        }

        if (valorLitro == null) {
            logger.warn("Valor litro no disponible, usando 0.0");
            valorLitro = 0.0;
        }
        if (costoBaseGestionFijo == null) {
            logger.warn("Costo base de gestión no disponible, usando 0.0");
            costoBaseGestionFijo = 0.0;
        }

        double costoTotal = 0.0;
        List<Map<String, Object>> costosPorTramo = new java.util.ArrayList<>();

        // Costo de gestión total (según requerimiento: valor fijo en base a la cantidad de tramos)
        double costoGestionTotal = costoBaseGestionFijo * tramos.size();

        for (int i = 0; i < tramos.size(); i++) {
            Tramo tramo = tramos.get(i);
            double distancia = tramo.getDistancia() != null ? tramo.getDistancia() : 0.0;

            // Costos por km del camión
            double costoKmCamion = 0.0;
            double costoCombustible = 0.0;
            double costoEstadia = 0.0;

            // Obtener datos del camión por dominio si existe
            if (tramo.getCamionDominio() != null && !tramo.getCamionDominio().isEmpty()) {
                try {
                    java.util.Optional<com.backend.tpi.ms_rutas_transportistas.models.Camion> camionOpt = camionRepository.findFirstByDominio(tramo.getCamionDominio());
                    if (camionOpt.isPresent()) {
                        com.backend.tpi.ms_rutas_transportistas.models.Camion camion = camionOpt.get();
                        if (camion.getCostoPorKm() != null) costoKmCamion = camion.getCostoPorKm() * distancia;
                        if (camion.getConsumoCombustiblePromedio() != null) {
                            double consumoLitros = camion.getConsumoCombustiblePromedio() * distancia;
                            costoCombustible = consumoLitros * valorLitro;
                        }
                    }
                } catch (Exception e) {
                    logger.warn("No se pudo obtener datos del camión para dominio {}: {}", tramo.getCamionDominio(), e.getMessage());
                }
            }

            // Calcular costo de estadía para este tramo: el depósito de destino del tramo es el que aplica
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
                        // Determinar fecha de fin del tramo actual y fecha inicio del siguiente tramo
                        long noches = 0;
                        if (i + 1 < tramos.size()) {
                            // Para cálculo aproximado usamos únicamente las fechas estimadas
                            java.time.LocalDateTime finActualEst = tramo.getFechaHoraFinEstimada();
                            java.time.LocalDateTime inicioSiguienteEst = tramos.get(i + 1).getFechaHoraInicioEstimada();
                            if (finActualEst != null && inicioSiguienteEst != null) {
                                long n = java.time.temporal.ChronoUnit.DAYS.between(finActualEst.toLocalDate(), inicioSiguienteEst.toLocalDate());
                                noches = Math.max(0L, n);
                            } else {
                                noches = 0;
                            }
                        } else {
                            // No hay siguiente tramo --> no se considera estadía entre tramos
                            noches = 0;
                        }

                        if (noches < 0) noches = 0;
                        costoEstadia = noches * costoEstadiaDiario;
                    }
                } catch (Exception e) {
                    logger.warn("No se pudo obtener datos del depósito {}: {}", tramo.getDestinoDepositoId(), e.getMessage());
                }
            }

            double costoTramo = Math.round((costoKmCamion + costoCombustible + costoEstadia) * 100.0) / 100.0;

            // Actualizar costo aproximado del tramo
            tramo.setCostoAproximado(java.math.BigDecimal.valueOf(costoTramo));
            tramoRepository.save(tramo);

            Map<String, Object> infoTramo = new HashMap<>();
            infoTramo.put("tramoId", tramo.getId());
            infoTramo.put("orden", tramo.getOrden());
            infoTramo.put("distancia", distancia);
            infoTramo.put("costoPorKmCamion", Math.round(costoKmCamion * 100.0) / 100.0);
            infoTramo.put("costoCombustible", Math.round(costoCombustible * 100.0) / 100.0);
            infoTramo.put("costoEstadia", Math.round(costoEstadia * 100.0) / 100.0);
            infoTramo.put("costoTotalTramo", costoTramo);
            costosPorTramo.add(infoTramo);

            costoTotal += costoTramo;
        }

        // Agregar costo de gestión total
        costoTotal += costoGestionTotal;

        Map<String, Object> resultado = new HashMap<>();
        resultado.put("rutaId", rutaId);
        resultado.put("costoTotal", Math.round(costoTotal * 100.0) / 100.0);
        resultado.put("costoGestionTotal", Math.round(costoGestionTotal * 100.0) / 100.0);
        resultado.put("valorLitro", valorLitro);
        resultado.put("numeroTramos", tramos.size());
        resultado.put("costosPorTramo", costosPorTramo);
        resultado.put("exitoso", true);
        resultado.put("mensaje", String.format("Costos calculados para %d tramos", tramos.size()));

        logger.info("Cálculo de costos finalizado - Costo total: {}, Tramos: {}", resultado.get("costoTotal"), tramos.size());
        return resultado;
    }

    /**
     * Obtiene la tarifa por kilómetro desde el microservicio de cálculos
     * @return Tarifa por km, o null si no se puede obtener
     */
    private Double obtenerTarifaPorKm() {
        try {
            String token = extractBearerToken();
            
            org.springframework.http.ResponseEntity<java.util.List<java.util.Map<String, Object>>> response = 
                calculosClient.get()
                    .uri("/api/v1/tarifas")
                    .headers(h -> { if (token != null) h.setBearerAuth(token); })
                    .retrieve()
                    .toEntity(new org.springframework.core.ParameterizedTypeReference<java.util.List<java.util.Map<String, Object>>>() {});
            
            java.util.List<java.util.Map<String, Object>> tarifas = response.getBody();
            
            if (tarifas != null && !tarifas.isEmpty()) {
                Object precioPorKm = tarifas.get(0).get("precioPorKm");
                if (precioPorKm instanceof Number) {
                    return ((Number) precioPorKm).doubleValue();
                }
            }
            
            logger.warn("No se encontraron tarifas en el servicio de cálculos");
            return null;
            
        } catch (Exception e) {
            logger.error("Error al obtener tarifa por km: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Helper: extrae token Bearer del SecurityContext si existe
     */
    private String extractBearerToken() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken) {
            return ((org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken) auth).getToken().getTokenValue();
        }
        return null;
    }

    /**
     * Genera opciones tentativas para una solicitud sin persistir una Ruta.
     * Consulta ms-solicitudes para obtener coordenadas de origen/destino y busca los depósitos
     * más cercanos usando ms-gestion-calculos, luego delega a RutaTentativaService.
     * @param solicitudId id de la solicitud
     * @return lista de opciones tentativas
     */
    public List<RutaTentativaDTO> generateOptionsForSolicitud(Long solicitudId) {
        logger.info("Generate options for solicitud {}", solicitudId);
        try {
            String token = extractBearerToken();
            // Obtener solicitud desde ms-solicitudes
            org.springframework.http.ResponseEntity<java.util.Map<String, Object>> solicitudEntity = solicitudesClient.get()
                    .uri("/api/v1/solicitudes/{id}", solicitudId)
                    .headers(h -> { if (token != null) h.setBearerAuth(token); })
                    .retrieve()
                    .toEntity(new org.springframework.core.ParameterizedTypeReference<java.util.Map<String, Object>>() {});

            java.util.Map<String, Object> solicitud = solicitudEntity != null ? solicitudEntity.getBody() : null;
            if (solicitud == null) {
                throw new IllegalArgumentException("Solicitud not found: " + solicitudId);
            }

            Double origenLat = null; Double origenLong = null; Double destinoLat = null; Double destinoLong = null;
            if (solicitud.get("origenLat") instanceof Number) origenLat = ((Number) solicitud.get("origenLat")).doubleValue();
            if (solicitud.get("origenLong") instanceof Number) origenLong = ((Number) solicitud.get("origenLong")).doubleValue();
            if (solicitud.get("destinoLat") instanceof Number) destinoLat = ((Number) solicitud.get("destinoLat")).doubleValue();
            if (solicitud.get("destinoLong") instanceof Number) destinoLong = ((Number) solicitud.get("destinoLong")).doubleValue();

            if (origenLat == null || origenLong == null || destinoLat == null || destinoLong == null) {
                throw new IllegalArgumentException("Solicitud does not contain coordinates to determine nearest deposits");
            }

            // Obtener lista de depósitos completos
            org.springframework.http.ResponseEntity<java.util.List<java.util.Map<String, Object>>> depsResp = calculosClient.get()
                    .uri("/api/v1/depositos")
                    .headers(h -> { if (token != null) h.setBearerAuth(token); })
                    .retrieve()
                    .toEntity(new org.springframework.core.ParameterizedTypeReference<java.util.List<java.util.Map<String, Object>>>() {});

            java.util.List<java.util.Map<String, Object>> depositos = depsResp != null ? depsResp.getBody() : null;
            if (depositos == null || depositos.isEmpty()) {
                logger.error("No se encontraron depósitos en el sistema. Se requiere al menos un depósito para calcular rutas.");
                throw new IllegalStateException("No hay depósitos disponibles en el sistema para calcular rutas. " +
                    "Por favor, registre al menos un depósito en el microservicio de cálculos (POST /api/v1/depositos) " +
                    "antes de solicitar opciones de ruta.");
            }

            // Encontrar depósito más cercano al origen y al destino
            Long origenDepotId = null; Long destinoDepotId = null;
            double minOrigDist = Double.MAX_VALUE; double minDestDist = Double.MAX_VALUE;
            for (var d : depositos) {
                if (d.get("latitud") == null || d.get("longitud") == null || d.get("id") == null) continue;
                double lat = ((Number)d.get("latitud")).doubleValue();
                double lon = ((Number)d.get("longitud")).doubleValue();
                double distOrig = distanceKm(origenLat, origenLong, lat, lon);
                double distDest = distanceKm(destinoLat, destinoLong, lat, lon);
                Long id = ((Number)d.get("id")).longValue();
                if (distOrig < minOrigDist) { minOrigDist = distOrig; origenDepotId = id; }
                if (distDest < minDestDist) { minDestDist = distDest; destinoDepotId = id; }
            }

            if (origenDepotId == null || destinoDepotId == null) {
                throw new IllegalStateException("Unable to determine nearest deposits");
            }

            logger.info("Nearest deposits for solicitud {} -> origen: {}, destino: {}", solicitudId, origenDepotId, destinoDepotId);

            // Delegar a RutaTentativaService con coordenadas reales
            List<RutaTentativaDTO> variantes = rutaTentativaService.calcularVariantesCompletas(
                origenLat, origenLong, destinoLat, destinoLong, 
                origenDepotId, destinoDepotId);
            return variantes;
        } catch (Exception e) {
            logger.error("Error generating options for solicitud {}: {}", solicitudId, e.getMessage());
            throw new RuntimeException(e);
        }
    }

    // Haversine formula for approximate distance in km
    private double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371; // Earth radius km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon/2) * Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return R * c;
    }

    // createFromTentativa removed: this method was only used by the deprecated controller
    // flow that has been removed. Use the persisted opciones + confirmar flow under
    // `/api/v1/solicitudes/{id}/opciones` and `confirmar` which delegates to existing
    // service methods that remain.

    /**
     * Crea una Ruta definitiva a partir de una RutaTentativa (usada por la confirmación de opción persistida)
     * @param solicitudId id de la solicitud asociada
     * @param rutaTentativa datos de la ruta tentativa (tramos, distancias, duraciones)
     * @return DTO de la Ruta creada
     */
    @org.springframework.transaction.annotation.Transactional
    public RutaDTO createFromTentativa(Long solicitudId, RutaTentativaDTO rutaTentativa) {
        if (solicitudId == null) throw new IllegalArgumentException("SolicitudId is required");
        if (rutaTentativa == null) throw new IllegalArgumentException("RutaTentativa is required");

        logger.info("=== createFromTentativa: solicitud={} ===", solicitudId);
        logger.info("RutaTentativa: tramos={}, distTotal={}, durTotal={}", 
            rutaTentativa.getTramos() != null ? rutaTentativa.getTramos().size() : 0,
            rutaTentativa.getDistanciaTotal(), rutaTentativa.getDuracionTotalHoras());

        // Verificar si ya existe una ruta para esta solicitud
        Optional<Ruta> rutaExistente = rutaRepository.findByIdSolicitud(solicitudId);
        if (rutaExistente.isPresent()) {
            throw new IllegalArgumentException("Ya existe una ruta para la solicitud ID: " + solicitudId);
        }

        Ruta ruta = new Ruta();
        ruta.setIdSolicitud(solicitudId);
        ruta = rutaRepository.save(ruta);

        // Crear tramos según la ruta tentativa con fechas estimadas
        if (rutaTentativa.getTramos() != null) {
            logger.info("Creando {} tramos para ruta {} con fechas estimadas", rutaTentativa.getTramos().size(), ruta.getId());
            
            // Obtener fecha de creación de la solicitud
            java.time.LocalDateTime fechaCreacionSolicitud = java.time.LocalDateTime.now();
            try {
                String token = extractBearerToken();
                org.springframework.http.ResponseEntity<com.backend.tpi.ms_rutas_transportistas.dtos.SolicitudIntegrationDTO> solicitudEntity = 
                    solicitudesClient.get()
                        .uri("/api/v1/solicitudes/{id}", solicitudId)
                        .headers(h -> { if (token != null) h.setBearerAuth(token); })
                        .retrieve()
                        .toEntity(com.backend.tpi.ms_rutas_transportistas.dtos.SolicitudIntegrationDTO.class);
                
                com.backend.tpi.ms_rutas_transportistas.dtos.SolicitudIntegrationDTO solicitud = solicitudEntity.getBody();
                if (solicitud != null && solicitud.getFechaCreacion() != null) {
                    fechaCreacionSolicitud = solicitud.getFechaCreacion();
                    logger.info("Fecha de creación de solicitud obtenida: {}", fechaCreacionSolicitud);
                }
            } catch (Exception e) {
                logger.warn("No se pudo obtener fecha de creación de solicitud, usando fecha actual: {}", e.getMessage());
            }
            
            // Fecha de inicio: al día siguiente de la creación de la solicitud a las 00:00
            java.time.LocalDateTime fechaActual = fechaCreacionSolicitud.plusDays(1).toLocalDate().atStartOfDay();
            logger.info("Fecha de inicio de primer tramo (día siguiente a creación): {}", fechaActual);
            
            int creados = 0;
            for (TramoTentativoDTO t : rutaTentativa.getTramos()) {
                logger.info("  Creando tramo: orden={}, origenDepId={}, destinoDepId={}, dist={}, duracion={}h", 
                    t.getOrden(), t.getOrigenDepositoId(), t.getDestinoDepositoId(), t.getDistanciaKm(), t.getDuracionHoras());
                
                Tramo tramo = new Tramo();
                tramo.setRuta(ruta);
                tramo.setOrden(t.getOrden());
                tramo.setOrigenDepositoId(t.getOrigenDepositoId());
                tramo.setDestinoDepositoId(t.getDestinoDepositoId());
                tramo.setDistancia(t.getDistanciaKm());
                tramo.setDuracionHoras(t.getDuracionHoras());
                tramo.setGeneradoAutomaticamente(true);
                
                // Obtener token de autenticación (no necesario aquí cuando usamos DepositoService)
                
                // Consultar y guardar coordenadas del depósito de origen si existe
                if (t.getOrigenDepositoId() != null) {
                    try {
                        java.util.Map<Long, java.util.Map<String, Object>> info = depositoService.getInfoForDepositos(java.util.List.of(t.getOrigenDepositoId()));
                        java.util.Map<String, Object> deposito = info != null ? info.get(t.getOrigenDepositoId()) : null;
                        if (deposito != null) {
                            Object lat = deposito.get("latitud");
                            Object lon = deposito.get("longitud");
                            if (lat instanceof Number && lon instanceof Number) {
                                tramo.setOrigenLat(java.math.BigDecimal.valueOf(((Number) lat).doubleValue()));
                                tramo.setOrigenLong(java.math.BigDecimal.valueOf(((Number) lon).doubleValue()));
                                logger.info("    Coordenadas de depósito origen {}: lat={}, lon={}",
                                        t.getOrigenDepositoId(), lat, lon);
                            } else {
                                logger.warn("Depósito {} no contiene latitud/longitud válidas: {}", t.getOrigenDepositoId(), deposito);
                            }
                        } else {
                            logger.warn("No se encontró info del depósito origen {} desde DepositoService", t.getOrigenDepositoId());
                        }
                    } catch (Exception e) {
                        logger.warn("No se pudieron obtener coordenadas del depósito origen {}: {}", t.getOrigenDepositoId(), e.getMessage());
                    }
                } else if (t.getOrigenLat() != null && t.getOrigenLong() != null) {
                    // Guardar coordenadas del punto de origen si no es un depósito (origen de solicitud)
                    tramo.setOrigenLat(java.math.BigDecimal.valueOf(t.getOrigenLat()));
                    tramo.setOrigenLong(java.math.BigDecimal.valueOf(t.getOrigenLong()));
                }
                
                // Consultar y guardar coordenadas del depósito de destino si existe
                if (t.getDestinoDepositoId() != null) {
                    try {
                        java.util.Map<Long, java.util.Map<String, Object>> info = depositoService.getInfoForDepositos(java.util.List.of(t.getDestinoDepositoId()));
                        java.util.Map<String, Object> deposito = info != null ? info.get(t.getDestinoDepositoId()) : null;
                        if (deposito != null) {
                            Object lat = deposito.get("latitud");
                            Object lon = deposito.get("longitud");
                            if (lat instanceof Number && lon instanceof Number) {
                                tramo.setDestinoLat(java.math.BigDecimal.valueOf(((Number) lat).doubleValue()));
                                tramo.setDestinoLong(java.math.BigDecimal.valueOf(((Number) lon).doubleValue()));
                                logger.info("    Coordenadas de depósito destino {}: lat={}, lon={}",
                                        t.getDestinoDepositoId(), lat, lon);
                            } else {
                                logger.warn("Depósito {} no contiene latitud/longitud válidas: {}", t.getDestinoDepositoId(), deposito);
                            }
                        } else {
                            logger.warn("No se encontró info del depósito destino {} desde DepositoService", t.getDestinoDepositoId());
                        }
                    } catch (Exception e) {
                        logger.warn("No se pudieron obtener coordenadas del depósito destino {}: {}", t.getDestinoDepositoId(), e.getMessage());
                    }
                } else if (t.getDestinoLat() != null && t.getDestinoLong() != null) {
                    // Guardar coordenadas del punto de destino si no es un depósito (destino de solicitud)
                    tramo.setDestinoLat(java.math.BigDecimal.valueOf(t.getDestinoLat()));
                    tramo.setDestinoLong(java.math.BigDecimal.valueOf(t.getDestinoLong()));
                }
                
                // Calcular fechas estimadas
                // Fecha de inicio del tramo = fecha actual acumulada
                tramo.setFechaHoraInicioEstimada(fechaActual);
                
                // Fecha de fin del tramo = fecha inicio + duración del viaje
                double duracionHoras = t.getDuracionHoras() != null ? t.getDuracionHoras() : 0.0;
                long minutosDuracion = (long) (duracionHoras * 60);
                java.time.LocalDateTime fechaFinTramo = fechaActual.plusMinutes(minutosDuracion);
                tramo.setFechaHoraFinEstimada(fechaFinTramo);
                
                logger.info("    Fechas estimadas: inicio={}, fin={}", 
                    tramo.getFechaHoraInicioEstimada(), tramo.getFechaHoraFinEstimada());
                
                // Guardar el tramo
                tramoService.save(tramo);
                creados++;
                
                // Actualizar fecha actual para el próximo tramo
                // Si el tramo termina en un depósito (no es el último tramo), agregar tiempo de estadía
                Long depositoDestinoId = t.getDestinoDepositoId();
                logger.warn("    ===>>> DEBUG ANTES DEL IF: destinoDepositoId={}, tipo={}", 
                    depositoDestinoId, depositoDestinoId != null ? depositoDestinoId.getClass().getName() : "null");
                
                if (depositoDestinoId != null) {
                    logger.warn("    ===>>> DENTRO DEL IF: agregando {} horas de estadía", estadiaDepositoHoras);
                    // Hay un depósito de destino, agregar tiempo de estadía
                    long minutosEstadia = (long) (estadiaDepositoHoras * 60);
                    fechaActual = fechaFinTramo.plusMinutes(minutosEstadia);
                    logger.warn("    ===>>> Estadía aplicada en depósito {}: {} horas. Próximo inicio: {}", 
                        depositoDestinoId, estadiaDepositoHoras, fechaActual);
                } else {
                    logger.warn("    ===>>> DENTRO DEL ELSE: sin estadía");
                    // Es el último tramo (destino final), no hay estadía
                    fechaActual = fechaFinTramo;
                    logger.warn("    ===>>> Sin estadía (destino final). Próximo inicio: {}", fechaActual);
                }
            }
            logger.info("Total tramos creados: {} con fechas estimadas calculadas", creados);
        }

        // Intentar notificar al microservicio de solicitudes para asociar la ruta creada a la solicitud
        try {
            String token = extractBearerToken();
            solicitudesClient.patch()
                    .uri("/api/v1/solicitudes/" + solicitudId + "/ruta?rutaId=" + ruta.getId())
                    .headers(h -> { if (token != null) h.setBearerAuth(token); })
                    .retrieve()
                    .toEntity(Object.class);
            logger.info("Notificada solicitud {} con rutaId {} (createFromTentativa)", solicitudId, ruta.getId());
            
            // Cambiar estado de solicitud a PROGRAMADA cuando se confirma una ruta
            try {
                solicitudesClient.put()
                        .uri("/api/v1/solicitudes/" + solicitudId + "/estado?nuevoEstado=PROGRAMADA")
                        .headers(h -> { if (token != null) h.setBearerAuth(token); })
                        .retrieve()
                        .toBodilessEntity();
                logger.info("Estado de solicitud {} cambiado a PROGRAMADA tras confirmar ruta", solicitudId);
            } catch (Exception e) {
                logger.warn("No se pudo cambiar estado de solicitud a PROGRAMADA: {}", e.getMessage());
            }
        } catch (Exception e) {
            logger.warn("No se pudo notificar a ms-solicitudes para solicitud {}: {}", solicitudId, e.getMessage());
        }

        // NO calcular costos aquí porque sobrescribe las fechas estimadas que ya fueron calculadas correctamente
        // Los costos se pueden calcular posteriormente de forma manual si es necesario
        // Comentado para preservar las fechas estimadas con estadía de 24 horas
        /*
        try {
            logger.info("Calculando costos aproximados automáticamente para ruta {}", ruta.getId());
            calcularCostoRuta(ruta.getId());
            logger.info("Costos aproximados calculados exitosamente");
            
            // Refrescar la entidad ruta para que toDto obtenga los tramos actualizados
            ruta = rutaRepository.findById(ruta.getId()).orElse(ruta);
        } catch (Exception e) {
            logger.warn("No se pudieron calcular los costos aproximados automáticamente: {}", e.getMessage());
        }
        */

        return toDto(ruta);
    }
}

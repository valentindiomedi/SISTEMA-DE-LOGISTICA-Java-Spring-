package com.backend.tpi.ms_rutas_transportistas.services;

import com.backend.tpi.ms_rutas_transportistas.dtos.TramoTentativoDTO;
import com.backend.tpi.ms_rutas_transportistas.dtos.RutaTentativaDTO;
import com.backend.tpi.ms_rutas_transportistas.models.Ruta;
import com.backend.tpi.ms_rutas_transportistas.models.RutaOpcion;
import com.backend.tpi.ms_rutas_transportistas.models.Tramo;
import com.backend.tpi.ms_rutas_transportistas.repositories.RutaOpcionRepository;
import com.backend.tpi.ms_rutas_transportistas.repositories.RutaRepository;
import com.backend.tpi.ms_rutas_transportistas.repositories.TramoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@Service
@Slf4j
public class RutaOpcionService {

    @Autowired
    private RutaOpcionRepository rutaOpcionRepository;

    @Autowired
    private RutaRepository rutaRepository;

    @Autowired
    private TramoRepository tramoRepository;

    @Autowired
    private com.backend.tpi.ms_rutas_transportistas.repositories.TipoTramoRepository tipoTramoRepository;

    @Autowired
    private com.backend.tpi.ms_rutas_transportistas.repositories.EstadoTramoRepository estadoTramoRepository;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private DepositoService depositoService;

    @Value("${app.solicitudes.base-url:http://ms-solicitudes:8083}")
    private String solicitudesBaseUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // saveOptionsForRuta removed: route-based opciones persistence is deprecated and callers
    // should use `saveOptionsForSolicitud` or other canonical flows. Method removed to
    // avoid unused code while preserving service responsibilities for solicitud-based flows.

    @Transactional
    public List<RutaOpcion> saveOptionsForSolicitud(Long solicitudId, List<RutaTentativaDTO> opciones) throws Exception {
        List<RutaOpcion> saved = new ArrayList<>();
        int idx = 1;
        for (RutaTentativaDTO opcion : opciones) {
            log.info("=== Guardando opción {} para solicitud {} ===", idx, solicitudId);
            log.info("Tramos en RutaTentativaDTO: {}", opcion.getTramos() != null ? opcion.getTramos().size() : 0);
            if (opcion.getTramos() != null) {
                for (int i = 0; i < opcion.getTramos().size(); i++) {
                    TramoTentativoDTO t = opcion.getTramos().get(i);
                    log.info("  Tramo {}: orden={}, origenDepId={}, destinoDepId={}, dist={}", 
                        i+1, t.getOrden(), t.getOrigenDepositoId(), t.getDestinoDepositoId(), t.getDistanciaKm());
                }
            }
            
            RutaOpcion ro = new RutaOpcion();
            ro.setSolicitudId(solicitudId);
            ro.setOpcionIndex(idx++);
            ro.setDistanciaTotal(opcion.getDistanciaTotal());
            ro.setDuracionTotalHoras(opcion.getDuracionTotalHoras());
            ro.setDepositosIdsJson(objectMapper.writeValueAsString(opcion.getDepositosIds()));
            ro.setDepositosNombresJson(objectMapper.writeValueAsString(opcion.getDepositosNombres()));
            
            String tramosJsonStr = objectMapper.writeValueAsString(opcion.getTramos());
            log.info("TramosJson serializado (primeros 300 chars): {}", 
                tramosJsonStr.length() > 300 ? tramosJsonStr.substring(0, 300) + "..." : tramosJsonStr);
            ro.setTramosJson(tramosJsonStr);
            ro.setGeometry(opcion.getGeometry());
            saved.add(rutaOpcionRepository.save(ro));
        }
        return saved;
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<RutaOpcion> listOptionsForRuta(Long rutaId) {
        return rutaOpcionRepository.findByRutaIdOrderByOpcionIndex(rutaId);
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<RutaOpcion> listOptionsForSolicitud(Long solicitudId) {
        return rutaOpcionRepository.findBySolicitudIdOrderByOpcionIndex(solicitudId);
    }


    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public RutaOpcion findById(Long id) {
        return rutaOpcionRepository.findById(id).orElse(null);
    }

    @org.springframework.transaction.annotation.Transactional
    public void deleteBySolicitudId(Long solicitudId) {
        rutaOpcionRepository.deleteBySolicitudId(solicitudId);
    }

    @Transactional
    public Ruta selectOption(Long rutaId, Long opcionId) throws Exception {
        RutaOpcion opcion = rutaOpcionRepository.findById(opcionId)
                .orElseThrow(() -> new IllegalArgumentException("Opción no encontrada: " + opcionId));
        if (!rutaId.equals(opcion.getRutaId())) {
            throw new IllegalArgumentException("La opción no pertenece a la ruta especificada");
        }

        // Obtener ruta
        Ruta ruta = rutaRepository.findById(rutaId)
                .orElseThrow(() -> new IllegalArgumentException("Ruta no encontrada: " + rutaId));

        // Eliminar tramos existentes
        List<Tramo> tramosExistentes = tramoRepository.findByRutaId(rutaId);
        if (tramosExistentes != null && !tramosExistentes.isEmpty()) {
            tramoRepository.deleteAll(tramosExistentes);
        }

        // Parsear tramos desde JSON
        TramoTentativoDTO[] tramosArr = objectMapper.readValue(opcion.getTramosJson(), TramoTentativoDTO[].class);
        int orden = 1;
            for (TramoTentativoDTO t : tramosArr) {
            Tramo tramo = new Tramo();
            tramo.setRuta(ruta);
            tramo.setOrden(orden++);
            tramo.setOrigenDepositoId(t.getOrigenDepositoId());
            tramo.setDestinoDepositoId(t.getDestinoDepositoId());
            tramo.setDistancia(t.getDistanciaKm());
            tramo.setDuracionHoras(t.getDuracionHoras());
            tramo.setGeneradoAutomaticamente(true);
                // Asignar coordenadas al crear el tramo (si vienen en el DTO o consultando el servicio de cálculos)
                try {
                    // Origen: si es depósito, consultar coordenadas desde DepositoService; si no, tomar del DTO
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
                                } else {
                                    log.warn("Depósito {} no contiene latitud/longitud válidas: {}", t.getOrigenDepositoId(), deposito);
                                }
                            } else {
                                log.warn("No se encontró info del depósito origen {} desde DepositoService", t.getOrigenDepositoId());
                            }
                        } catch (Exception e) {
                            log.warn("No se pudieron obtener coordenadas del depósito origen {}: {}", t.getOrigenDepositoId(), e.getMessage());
                        }
                    } else if (t.getOrigenLat() != null && t.getOrigenLong() != null) {
                        tramo.setOrigenLat(java.math.BigDecimal.valueOf(t.getOrigenLat()));
                        tramo.setOrigenLong(java.math.BigDecimal.valueOf(t.getOrigenLong()));
                    }

                    // Destino: si es depósito, consultar coordenadas desde DepositoService; si no, tomar del DTO
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
                                } else {
                                    log.warn("Depósito {} no contiene latitud/longitud válidas: {}", t.getDestinoDepositoId(), deposito);
                                }
                            } else {
                                log.warn("No se encontró info del depósito destino {} desde DepositoService", t.getDestinoDepositoId());
                            }
                        } catch (Exception e) {
                            log.warn("No se pudieron obtener coordenadas del depósito destino {}: {}", t.getDestinoDepositoId(), e.getMessage());
                        }
                    } else if (t.getDestinoLat() != null && t.getDestinoLong() != null) {
                        tramo.setDestinoLat(java.math.BigDecimal.valueOf(t.getDestinoLat()));
                        tramo.setDestinoLong(java.math.BigDecimal.valueOf(t.getDestinoLong()));
                    }
                } catch (Exception e) {
                    log.warn("Error asignando coordenadas al crear tramo en selectOption: {}", e.getMessage());
                }
            // Asignar tipoTramo por defecto si existe
            try {
                tipoTramoRepository.findAll().stream().findFirst().ifPresent(tramo::setTipoTramo);
            } catch (Exception e) {
                log.warn("No se pudo asignar tipoTramo por defecto: {}", e.getMessage());
            }
            // Asignar estado PENDIENTE por defecto si existe
            try {
                estadoTramoRepository.findByNombre("PENDIENTE").ifPresent(tramo::setEstado);
            } catch (Exception e) {
                log.warn("No se pudo asignar estadoTramo por defecto: {}", e.getMessage());
            }
            tramoRepository.save(tramo);
        }

        // Marcar opción seleccionada en la ruta
        ruta.setOpcionSeleccionadaId(opcionId);
        rutaRepository.save(ruta);
        log.info("Opción {} seleccionada para ruta {}", opcionId, rutaId);

        // Eliminar todas las opciones de la solicitud (ya que se seleccionó una)
        if (opcion.getSolicitudId() != null) {
            log.info("Eliminando opciones restantes de la solicitud {}", opcion.getSolicitudId());
            deleteBySolicitudId(opcion.getSolicitudId());
        }

        // Si la ruta está asociada a una solicitud, notificar a ms-solicitudes
        if (ruta.getIdSolicitud() != null) {
            Long solicitudId = ruta.getIdSolicitud();
            try {
                String token = extractBearerToken();
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                if (token != null) {
                    headers.setBearerAuth(token);
                }

                // Construir URL PATCH: /api/v1/solicitudes/{id}/ruta?rutaId={rutaId}
                String url = String.format("%s/api/v1/solicitudes/%d/ruta?rutaId=%d", solicitudesBaseUrl, solicitudId, ruta.getId());
                HttpEntity<String> entity = new HttpEntity<>("{}", headers);

                ResponseEntity<Void> resp = restTemplate.exchange(url, HttpMethod.PATCH, entity, Void.class);
                if (!resp.getStatusCode().is2xxSuccessful()) {
                    throw new RuntimeException("ms-solicitudes returned non-2xx: " + resp.getStatusCodeValue());
                }
                log.info("Notificada ms-solicitudes: solicitud {} asociada a ruta {}", solicitudId, ruta.getId());
            } catch (Exception e) {
                log.error("Error notificando a ms-solicitudes para solicitud {}: {}", ruta.getIdSolicitud(), e.getMessage());
                // Propagar excepción para que la transacción haga rollback y el caller reciba error (síncrono)
                throw e;
            }
        }

        return ruta;
    }

    private String extractBearerToken() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken) {
            return ((JwtAuthenticationToken) auth).getToken().getTokenValue();
        }
        return null;
    }

}


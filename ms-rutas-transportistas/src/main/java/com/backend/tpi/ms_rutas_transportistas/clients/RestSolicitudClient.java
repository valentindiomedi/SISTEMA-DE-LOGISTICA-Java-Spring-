package com.backend.tpi.ms_rutas_transportistas.clients;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@Component
public class RestSolicitudClient implements SolicitudClient {

    @Autowired
    private org.springframework.web.client.RestClient solicitudesClient;

    private String extractBearerToken() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken) {
            return ((JwtAuthenticationToken) auth).getToken().getTokenValue();
        }
        return null;
    }

    @Override
    public void cambiarEstado(Long solicitudId, String nuevoEstado) {
        try {
            String token = extractBearerToken();
            solicitudesClient.put()
                    .uri("/api/v1/solicitudes/" + solicitudId + "/estado?nuevoEstado=" + nuevoEstado)
                    .headers(h -> { if (token != null) h.setBearerAuth(token); })
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            throw new RuntimeException("Error cambiando estado de solicitud: " + e.getMessage(), e);
        }
    }

    @Override
    public void finalizarSolicitud(Long solicitudId, double costoFinal, double tiempoReal) {
        try {
            String token = extractBearerToken();
            String uri = String.format("/api/v1/solicitudes/%d/finalizar?costoFinal=%.2f&tiempoReal=%.2f", solicitudId, costoFinal, tiempoReal);
            solicitudesClient.patch()
                    .uri(uri)
                    .headers(h -> { if (token != null) h.setBearerAuth(token); })
                    .retrieve()
                    .toEntity(Object.class);
        } catch (Exception e) {
            throw new RuntimeException("Error finalizando solicitud: " + e.getMessage(), e);
        }
    }
}

package com.backend.tpi.ms_rutas_transportistas.clients;

public interface SolicitudClient {
    void cambiarEstado(Long solicitudId, String nuevoEstado);
    void finalizarSolicitud(Long solicitudId, double costoFinal, double tiempoReal);
}

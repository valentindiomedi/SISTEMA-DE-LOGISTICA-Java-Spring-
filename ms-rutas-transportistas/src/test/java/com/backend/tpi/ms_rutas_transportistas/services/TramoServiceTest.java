package com.backend.tpi.ms_rutas_transportistas.services;

import com.backend.tpi.ms_rutas_transportistas.clients.SolicitudClient;
import com.backend.tpi.ms_rutas_transportistas.models.Ruta;
import com.backend.tpi.ms_rutas_transportistas.models.Tramo;
import com.backend.tpi.ms_rutas_transportistas.models.EstadoTramo;
import com.backend.tpi.ms_rutas_transportistas.repositories.TramoRepository;
import com.backend.tpi.ms_rutas_transportistas.repositories.EstadoTramoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TramoServiceTest {

    @InjectMocks
    private TramoService tramoService;

    @Mock
    private TramoRepository tramoRepository;

    @Mock
    private EstadoTramoRepository estadoTramoRepository;

    @Mock
    private SolicitudClient solicitudClient;

    @Test
    public void updateFechaLlegada_whenAllTramosCompleted_callsSolicitudCompleta() {
        Long tramoId = 1L;
        Long rutaId = 10L;
        Long solicitudId = 100L;

        Tramo tramo = new Tramo();
        tramo.setId(tramoId);
        Ruta ruta = new Ruta();
        ruta.setId(rutaId);
        ruta.setIdSolicitud(solicitudId);
        tramo.setRuta(ruta);

        // Simulate that after updating, all tramos for the route have fechaHoraFinReal
        Tramo other = new Tramo();
        other.setId(2L);
        other.setFechaHoraFinReal(LocalDateTime.now());

        Tramo thisWithFinish = new Tramo();
        thisWithFinish.setId(tramoId);
        thisWithFinish.setFechaHoraFinReal(LocalDateTime.now());

        when(tramoRepository.findById(tramoId)).thenReturn(Optional.of(tramo));
        when(tramoRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(tramoRepository.findByRutaId(rutaId)).thenReturn(Arrays.asList(thisWithFinish, other));
        when(estadoTramoRepository.findByNombre("COMPLETADO")).thenReturn(Optional.of(new EstadoTramo()));

        tramoService.updateFechaLlegada(tramoId, LocalDateTime.now());

        verify(solicitudClient, times(1)).cambiarEstado(solicitudId, "COMPLETADA");
    }
}

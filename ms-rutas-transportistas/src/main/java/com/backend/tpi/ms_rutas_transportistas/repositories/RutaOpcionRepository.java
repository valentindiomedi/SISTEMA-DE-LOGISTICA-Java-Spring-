package com.backend.tpi.ms_rutas_transportistas.repositories;

import com.backend.tpi.ms_rutas_transportistas.models.RutaOpcion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RutaOpcionRepository extends JpaRepository<RutaOpcion, Long> {
    List<RutaOpcion> findByRutaIdOrderByOpcionIndex(Long rutaId);
    List<RutaOpcion> findBySolicitudIdOrderByOpcionIndex(Long solicitudId);
    void deleteBySolicitudId(Long solicitudId);
}

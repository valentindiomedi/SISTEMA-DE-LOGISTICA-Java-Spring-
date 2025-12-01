package com.backend.tpi.ms_rutas_transportistas.repositories;

import com.backend.tpi.ms_rutas_transportistas.models.EstadoTramo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio JPA para la entidad EstadoTramo
 * Gestiona los estados posibles de un tramo (pendiente, en curso, finalizado)
 */
@Repository
public interface EstadoTramoRepository extends JpaRepository<EstadoTramo, Long> {
	java.util.Optional<EstadoTramo> findByNombre(String nombre);
}

package com.backend.tpi.ms_rutas_transportistas.repositories;

import com.backend.tpi.ms_rutas_transportistas.models.TipoTramo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio JPA para la entidad TipoTramo
 * Gestiona los tipos de tramo (origen-depósito, depósito-depósito, depósito-destino)
 */
@Repository
public interface TipoTramoRepository extends JpaRepository<TipoTramo, Long> {
	java.util.Optional<TipoTramo> findByNombre(String nombre);
}

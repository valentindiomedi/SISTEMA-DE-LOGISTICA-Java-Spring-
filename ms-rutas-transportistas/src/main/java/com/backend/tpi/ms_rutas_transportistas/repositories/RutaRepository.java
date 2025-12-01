package com.backend.tpi.ms_rutas_transportistas.repositories;

import com.backend.tpi.ms_rutas_transportistas.models.Ruta;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio JPA para la entidad Ruta
 * Gestiona las operaciones de persistencia de las rutas de transporte
 */
@Repository
public interface RutaRepository extends JpaRepository<Ruta, Long> {
	/**
	 * Busca la ruta asociada a una solicitud específica
	 * @param idSolicitud ID de la solicitud
	 * @return Ruta asociada a la solicitud, si existe
	 */
	Optional<Ruta> findByIdSolicitud(Long idSolicitud);
}

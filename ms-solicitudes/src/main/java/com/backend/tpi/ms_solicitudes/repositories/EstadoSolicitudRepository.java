package com.backend.tpi.ms_solicitudes.repositories;

import com.backend.tpi.ms_solicitudes.models.EstadoSolicitud;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio para acceso a datos de Estados de Solicitud
 */
@Repository
public interface EstadoSolicitudRepository extends JpaRepository<EstadoSolicitud, Long> {
	/**
	 * Busca un estado de solicitud por nombre
	 * @param nombre Nombre del estado
	 * @return Estado encontrado (opcional)
	 */
	java.util.Optional<EstadoSolicitud> findByNombre(String nombre);
}

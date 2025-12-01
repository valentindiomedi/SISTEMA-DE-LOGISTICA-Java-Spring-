package com.backend.tpi.ms_solicitudes.repositories;

import com.backend.tpi.ms_solicitudes.models.EstadoContenedor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio para acceso a datos de Estados de Contenedor
 */
@Repository
public interface EstadoContenedorRepository extends JpaRepository<EstadoContenedor, Long> {
	/**
	 * Busca un estado de contenedor por nombre
	 * @param nombre Nombre del estado
	 * @return Estado encontrado (opcional)
	 */
	java.util.Optional<EstadoContenedor> findByNombre(String nombre);
}

package com.backend.tpi.ms_solicitudes.repositories;

import com.backend.tpi.ms_solicitudes.models.Contenedor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio para acceso a datos de Contenedores
 */
@Repository
public interface ContenedorRepository extends JpaRepository<Contenedor, Long> {
	/**
	 * Busca contenedores por cliente
	 * @param clienteId ID del cliente
	 * @return Lista de contenedores del cliente
	 */
	java.util.List<Contenedor> findByClienteId(Long clienteId);
	
	/**
	 * Busca contenedores por estado
	 * @param estado Estado del contenedor
	 * @return Lista de contenedores con ese estado
	 */
	java.util.List<Contenedor> findByEstado(com.backend.tpi.ms_solicitudes.models.EstadoContenedor estado);
	
	/**
	 * Cuenta contenedores de un cliente
	 * @param clienteId ID del cliente
	 * @return Cantidad de contenedores del cliente
	 */
	long countByClienteId(Long clienteId);
}

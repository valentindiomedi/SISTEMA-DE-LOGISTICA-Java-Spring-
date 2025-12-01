package com.backend.tpi.ms_solicitudes.repositories;

import com.backend.tpi.ms_solicitudes.models.Solicitud;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio para acceso a datos de Solicitudes
 */
@Repository
public interface SolicitudRepository extends JpaRepository<Solicitud, Long> {
	/**
	 * Busca solicitudes por cliente
	 * @param clienteId ID del cliente
	 * @return Lista de solicitudes del cliente
	 */
	java.util.List<Solicitud> findByClienteId(Long clienteId);

	/**
	 * Busca solicitudes por nombre de estado (join automático por propiedad estado.nombre)
	 * @param nombre Nombre del estado
	 * @return Lista de solicitudes con ese estado
	 */
	java.util.List<Solicitud> findByEstado_Nombre(String nombre);

	/**
	 * Busca solicitudes por ruta
	 * @param rutaId ID de la ruta
	 * @return Lista de solicitudes asociadas a esa ruta
	 */
	java.util.List<Solicitud> findByRutaId(Long rutaId);

	/**
	 * Busca la solicitud activa más reciente por contenedor (para seguimiento)
	 * @param contenedorId ID del contenedor
	 * @return Solicitud activa del contenedor
	 */
	java.util.Optional<Solicitud> findFirstByContenedor_IdOrderByIdDesc(Long contenedorId);
	
	/**
	 * Cuenta solicitudes de un contenedor
	 * @param contenedorId ID del contenedor
	 * @return Cantidad de solicitudes del contenedor
	 */
	long countByContenedor_Id(Long contenedorId);
	
	/**
	 * Cuenta solicitudes de una ruta
	 * @param rutaId ID de la ruta
	 * @return Cantidad de solicitudes de la ruta
	 */
	long countByRutaId(Long rutaId);
	
	/**
	 * Busca todas las solicitudes de un contenedor
	 * @param contenedorId ID del contenedor
	 * @return Lista de solicitudes del contenedor
	 */
	java.util.List<Solicitud> findByContenedor_Id(Long contenedorId);
}

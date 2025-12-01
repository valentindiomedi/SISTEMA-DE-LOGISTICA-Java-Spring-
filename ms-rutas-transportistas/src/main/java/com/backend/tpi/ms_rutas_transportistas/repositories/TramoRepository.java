package com.backend.tpi.ms_rutas_transportistas.repositories;

import com.backend.tpi.ms_rutas_transportistas.models.Tramo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio JPA para la entidad Tramo
 * Gestiona las operaciones de persistencia de los tramos de ruta
 */
@Repository
public interface TramoRepository extends JpaRepository<Tramo, Long> {
	/**
	 * Busca todos los tramos de una ruta específica
	 * @param rutaId ID de la ruta
	 * @return Lista de tramos de la ruta
	 */
	java.util.List<Tramo> findByRutaId(Long rutaId);
	
	/**
	 * Busca todos los tramos de una ruta específica ordenados por orden ascendente
	 * @param rutaId ID de la ruta
	 * @return Lista de tramos de la ruta ordenados por campo orden
	 */
	java.util.List<Tramo> findByRutaIdOrderByOrdenAsc(Long rutaId);
	
	/**
	 * Cuenta los tramos de una ruta específica
	 * @param rutaId ID de la ruta
	 * @return Cantidad de tramos de la ruta
	 */
	long countByRutaId(Long rutaId);
}

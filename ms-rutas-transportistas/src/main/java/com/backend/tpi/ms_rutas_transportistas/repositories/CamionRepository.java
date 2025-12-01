package com.backend.tpi.ms_rutas_transportistas.repositories;

import com.backend.tpi.ms_rutas_transportistas.models.Camion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio JPA para la entidad Camion
 * Gestiona las operaciones de persistencia de los camiones y sus asignaciones
 */
@Repository
public interface CamionRepository extends JpaRepository<Camion, Long> {
	// return the first match to avoid NonUniqueResultException when the DB contains
	// multiple rows with the same dominio. Business logic should ensure dominios
	// are unique, but being defensive here avoids runtime errors in production.
	java.util.Optional<Camion> findFirstByDominio(String dominio);
}

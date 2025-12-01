package com.backend.tpi.ms_gestion_calculos.repositories;

import com.backend.tpi.ms_gestion_calculos.models.Tarifa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio para acceso a datos de Tarifas
 */
@Repository
public interface TarifaRepository extends JpaRepository<Tarifa, Long> {
	/**
	 * Devuelve la tarifa más reciente (por id) para usar como tarifa activa por defecto
	 * @return Tarifa más reciente, o null si no hay tarifas
	 */
	Tarifa findTopByOrderByIdDesc();
}

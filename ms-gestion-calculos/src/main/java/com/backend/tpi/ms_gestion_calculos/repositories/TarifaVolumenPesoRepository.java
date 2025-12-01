package com.backend.tpi.ms_gestion_calculos.repositories;

import com.backend.tpi.ms_gestion_calculos.models.TarifaVolumenPeso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio para acceso a datos de Tarifas por Volumen y Peso
 * Gestiona los rangos de tarifas según características de la carga
 */
@Repository
public interface TarifaVolumenPesoRepository extends JpaRepository<TarifaVolumenPeso, Long> {
}

package com.backend.tpi.ms_rutas_transportistas.repositories;

import com.backend.tpi.ms_rutas_transportistas.models.EstadoCamion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio JPA para la entidad EstadoCamion
 * Gestiona los estados operativos de los camiones (disponible, en ruta, mantenimiento)
 */
@Repository
public interface EstadoCamionRepository extends JpaRepository<EstadoCamion, Long> {
}

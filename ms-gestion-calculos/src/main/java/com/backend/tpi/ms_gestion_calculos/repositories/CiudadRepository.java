package com.backend.tpi.ms_gestion_calculos.repositories;

import com.backend.tpi.ms_gestion_calculos.models.Ciudad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositorio para acceso a datos de Ciudades
 * Gestiona la información de ciudades y sus coordenadas
 */
@Repository
public interface CiudadRepository extends JpaRepository<Ciudad, Long> {
    
    /**
     * Busca una ciudad por su nombre (case-insensitive)
     * @param nombre Nombre de la ciudad
     * @return Ciudad si existe
     */
    Optional<Ciudad> findByNombreIgnoreCase(String nombre);
}

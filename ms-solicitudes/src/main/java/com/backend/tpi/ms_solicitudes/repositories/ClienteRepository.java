package com.backend.tpi.ms_solicitudes.repositories;

import com.backend.tpi.ms_solicitudes.models.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio para acceso a datos de Clientes
 */
@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Long> {
	/**
	 * Busca un cliente por su email
	 * @param email Email del cliente
	 * @return Cliente encontrado (opcional)
	 */
	java.util.Optional<Cliente> findByEmail(String email);
	java.util.Optional<Cliente> findByKeycloakUserId(String keycloakUserId);
}

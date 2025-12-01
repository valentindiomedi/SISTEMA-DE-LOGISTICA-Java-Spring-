package com.backend.tpi.ms_solicitudes.services;

import com.backend.tpi.ms_solicitudes.dto.ClienteRegistroDTO;
import com.backend.tpi.ms_solicitudes.dto.ClienteRegistroResponseDTO;
import com.backend.tpi.ms_solicitudes.models.Cliente;
import com.backend.tpi.ms_solicitudes.repositories.ClienteRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Servicio de negocio para Clientes
 * Maneja operaciones CRUD de clientes
 */
@Service
@Slf4j
public class ClienteService {

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private KeycloakService keycloakService;
    
    @Autowired
    private com.backend.tpi.ms_solicitudes.repositories.ContenedorRepository contenedorRepository;

    /**
     * Obtiene todos los clientes del sistema
     * @return Lista con todos los clientes
     */
    @Transactional(readOnly = true)
    public List<Cliente> findAll() {
        return clienteRepository.findAll();
    }

    /**
     * Busca un cliente por su email
     * @param email Email del cliente a buscar
     * @return Cliente encontrado
     * @throws RuntimeException si no se encuentra el cliente
     */
    @Transactional(readOnly = true)
    public Cliente findByEmail(String email) {
        return clienteRepository.findByEmail(email)
                .orElseThrow(() -> new com.backend.tpi.ms_solicitudes.exceptions.ResourceNotFoundException("Cliente", "email", email));
    }

    @Transactional(readOnly = true)
    public Cliente findByKeycloakUserId(String keycloakUserId) {
        return clienteRepository.findByKeycloakUserId(keycloakUserId)
                .orElseThrow(() -> new com.backend.tpi.ms_solicitudes.exceptions.ResourceNotFoundException("Cliente", "keycloakUserId", keycloakUserId));
    }

    /**
     * Busca un cliente por su ID
     * @param id ID del cliente
     * @return Cliente encontrado
     * @throws RuntimeException si no se encuentra el cliente
     */
    @Transactional(readOnly = true)
    public Cliente findById(Long id) {
        return clienteRepository.findById(id)
                .orElseThrow(() -> new com.backend.tpi.ms_solicitudes.exceptions.ResourceNotFoundException("Cliente", id));
    }

    /**
     * Guarda un nuevo cliente en la base de datos
     * @param cliente Cliente a guardar
     * @return Cliente guardado con su ID asignado
     */
    @Transactional
    public Cliente save(Cliente cliente) {
        log.info("Guardando cliente: {}", cliente.getNombre());
        return clienteRepository.save(cliente);
    }

    /**
     * Actualiza los datos de un cliente existente
     * @param id ID del cliente a actualizar
     * @param clienteActualizado Datos actualizados del cliente
     * @return Cliente actualizado
     */
    @Transactional
    public Cliente update(Long id, Cliente clienteActualizado) {
        Cliente cliente = findById(id);
        cliente.setNombre(clienteActualizado.getNombre());
        cliente.setEmail(clienteActualizado.getEmail());
        cliente.setTelefono(clienteActualizado.getTelefono());
        log.info("Actualizando cliente ID: {}", id);
        return clienteRepository.save(cliente);
    }

    /**
     * Elimina un cliente por su ID
     * @param id ID del cliente a eliminar
     * @throws RuntimeException si el cliente tiene contenedores asignados
     */
    @Transactional
    public void deleteById(Long id) {
        Cliente cliente = findById(id);
        
        // Validar que no tenga contenedores asignados
        long cantidadContenedores = contenedorRepository.countByClienteId(id);
        if (cantidadContenedores > 0) {
            throw new RuntimeException("No se puede eliminar el cliente ID " + id + 
                " porque tiene " + cantidadContenedores + " contenedor(es) asignado(s). " +
                "Debe eliminar primero los contenedores asociados.");
        }
        
        log.info("Eliminando cliente ID: {}", id);
        clienteRepository.delete(cliente);
    }

    /**
     * Registra un nuevo cliente creando usuario en Keycloak y guardando datos en BD
     * @param registroDTO Datos del cliente a registrar
     * @return Response con información del cliente creado
     * @throws RuntimeException si el username o email ya existen
     */
    @Transactional
    public ClienteRegistroResponseDTO registrarCliente(ClienteRegistroDTO registroDTO) {
        log.info("Iniciando registro de cliente: {}", registroDTO.getEmail());
        
        // Validar que el username no exista en Keycloak
        if (keycloakService.existeUsername(registroDTO.getUsername())) {
            throw new RuntimeException("El username ya está en uso");
        }
        
        // Validar que el email no exista en Keycloak
        if (keycloakService.existeEmail(registroDTO.getEmail())) {
            throw new RuntimeException("El email ya está registrado");
        }
        
        // Validar que el email no exista en la base de datos local
        if (clienteRepository.findByEmail(registroDTO.getEmail()).isPresent()) {
            throw new RuntimeException("El email ya está registrado en el sistema");
        }
        
        try {
            // Extraer nombre y apellido del nombre completo
            String[] nombrePartes = registroDTO.getNombre().trim().split("\\s+", 2);
            String firstName = nombrePartes[0];
            String lastName = nombrePartes.length > 1 ? nombrePartes[1] : "";
            
            // Crear usuario en Keycloak
            String keycloakUserId = keycloakService.crearUsuario(
                registroDTO.getUsername(),
                registroDTO.getEmail(),
                firstName,
                lastName,
                registroDTO.getPassword()
            );
            
            log.info("Usuario creado en Keycloak con ID: {}", keycloakUserId);
            
            // Crear cliente en la base de datos
            Cliente cliente = new Cliente();
            cliente.setNombre(registroDTO.getNombre());
            cliente.setEmail(registroDTO.getEmail());
            cliente.setTelefono(registroDTO.getTelefono());
            cliente.setKeycloakUserId(keycloakUserId);

            Cliente clienteGuardado = clienteRepository.save(cliente);
            log.info("Cliente guardado en BD con ID: {}", clienteGuardado.getId());
            
            // Crear respuesta
            ClienteRegistroResponseDTO response = new ClienteRegistroResponseDTO();
            response.setId(clienteGuardado.getId());
            response.setNombre(clienteGuardado.getNombre());
            response.setEmail(clienteGuardado.getEmail());
            response.setTelefono(clienteGuardado.getTelefono());
            response.setUsername(registroDTO.getUsername());
            response.setKeycloakUserId(keycloakUserId);
            response.setMensaje("Cliente registrado exitosamente. Puede iniciar sesión con sus credenciales.");
            
            return response;
            
        } catch (Exception e) {
            log.error("Error al registrar cliente: {}", e.getMessage(), e);
            throw new RuntimeException("Error al registrar cliente: " + e.getMessage(), e);
        }
    }
}

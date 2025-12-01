package com.backend.tpi.ms_solicitudes.services;

import com.backend.tpi.ms_solicitudes.dto.UsuarioDTO;
import com.backend.tpi.ms_solicitudes.dto.UsuarioRegistroDTO;
import com.backend.tpi.ms_solicitudes.dto.UsuarioRegistroResponseDTO;
import com.backend.tpi.ms_solicitudes.models.Cliente;
import com.backend.tpi.ms_solicitudes.repositories.ClienteRepository;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio para gestionar el registro de usuarios por parte del administrador
 */
@Service
@Slf4j
public class UsuarioService {

    @Autowired
    private KeycloakService keycloakService;

    @Autowired
    private ClienteRepository clienteRepository;

    /**
     * Registra un nuevo usuario en el sistema con el rol especificado
     * Solo los usuarios CLIENTE se guardan también en la base de datos local
     * @param registroDTO Datos del usuario a registrar
     * @return Response con información del usuario creado
     * @throws RuntimeException si el username o email ya existen
     */
    @Transactional
    public UsuarioRegistroResponseDTO registrarUsuario(UsuarioRegistroDTO registroDTO) {
        log.info("Iniciando registro de usuario: {} con rol: {}", registroDTO.getEmail(), registroDTO.getRol());
        
        // Validar que el username no exista en Keycloak
        if (keycloakService.existeUsername(registroDTO.getUsername())) {
            throw new RuntimeException("El username ya está en uso");
        }
        
        // Validar que el email no exista en Keycloak
        if (keycloakService.existeEmail(registroDTO.getEmail())) {
            throw new RuntimeException("El email ya está registrado");
        }
        
        // Si el rol es CLIENTE, validar que el email no exista en la base de datos local
        if ("CLIENTE".equals(registroDTO.getRol())) {
            if (clienteRepository.findByEmail(registroDTO.getEmail()).isPresent()) {
                throw new RuntimeException("El email ya está registrado en el sistema");
            }
        }
        
        try {
            // Extraer nombre y apellido del nombre completo
            String[] nombrePartes = registroDTO.getNombre().trim().split("\\s+", 2);
            String firstName = nombrePartes[0];
            String lastName = nombrePartes.length > 1 ? nombrePartes[1] : "";
            
            // Crear usuario en Keycloak con el rol especificado
            String keycloakUserId = keycloakService.crearUsuarioConRol(
                registroDTO.getUsername(),
                registroDTO.getEmail(),
                firstName,
                lastName,
                registroDTO.getPassword(),
                registroDTO.getRol()
            );
            
            log.info("Usuario creado en Keycloak con ID: {} y rol: {}", keycloakUserId, registroDTO.getRol());
            
            // Si el rol es CLIENTE, crear también en la base de datos local
            if ("CLIENTE".equals(registroDTO.getRol())) {
                Cliente cliente = new Cliente();
                cliente.setNombre(registroDTO.getNombre());
                cliente.setEmail(registroDTO.getEmail());
                cliente.setTelefono(registroDTO.getTelefono());
                cliente.setKeycloakUserId(keycloakUserId);

                clienteRepository.save(cliente);
                log.info("Cliente guardado en BD local");
            }
            
            // Crear respuesta
            UsuarioRegistroResponseDTO response = new UsuarioRegistroResponseDTO();
            response.setKeycloakUserId(keycloakUserId);
            response.setNombre(registroDTO.getNombre());
            response.setEmail(registroDTO.getEmail());
            response.setTelefono(registroDTO.getTelefono());
            response.setUsername(registroDTO.getUsername());
            response.setRol(registroDTO.getRol());
            response.setMensaje("Usuario registrado exitosamente con rol " + registroDTO.getRol() + ". Puede iniciar sesión con sus credenciales.");
            
            return response;
            
        } catch (Exception e) {
            log.error("Error al registrar usuario: {}", e.getMessage(), e);
            throw new RuntimeException("Error al registrar usuario: " + e.getMessage(), e);
        }
    }

    /**
     * Obtiene todos los usuarios del sistema
     * @return Lista de usuarios con sus datos básicos y roles
     */
    public List<UsuarioDTO> obtenerTodosLosUsuarios() {
        log.info("Obteniendo todos los usuarios del sistema");
        
        try {
            List<UserRepresentation> keycloakUsers = keycloakService.obtenerTodosLosUsuarios();
            
            return keycloakUsers.stream()
                .map(user -> {
                    UsuarioDTO dto = new UsuarioDTO();
                    dto.setId(user.getId());
                    dto.setUsername(user.getUsername());
                    dto.setEmail(user.getEmail());
                    dto.setNombre(user.getFirstName());
                    dto.setApellido(user.getLastName());
                    dto.setEnabled(user.isEnabled());
                    dto.setEmailVerified(user.isEmailVerified());
                    dto.setRoles(keycloakService.obtenerRolesUsuario(user.getId()));
                    return dto;
                })
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error al obtener todos los usuarios: {}", e.getMessage(), e);
            throw new RuntimeException("Error al obtener usuarios: " + e.getMessage(), e);
        }
    }
}

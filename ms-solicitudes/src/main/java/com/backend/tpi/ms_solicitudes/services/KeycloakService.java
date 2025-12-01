package com.backend.tpi.ms_solicitudes.services;

import com.backend.tpi.ms_solicitudes.config.KeycloakAdminConfig;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Servicio para gestionar usuarios en Keycloak
 * Permite crear usuarios y asignarles roles
 */
@Service
@Slf4j
public class KeycloakService {

    @Autowired
    private KeycloakAdminConfig keycloakAdminConfig;

    private Keycloak keycloak;

    /**
     * Obtiene una instancia de Keycloak Admin Client
     * @return Cliente de administración de Keycloak
     */
    private Keycloak getKeycloakInstance() {
        if (keycloak == null) {
            KeycloakBuilder builder = KeycloakBuilder.builder()
                    .serverUrl(keycloakAdminConfig.getServerUrl())
                    .realm(keycloakAdminConfig.getRealm())
                    .clientId(keycloakAdminConfig.getClientId())
                    .username(keycloakAdminConfig.getUsername())
                    .password(keycloakAdminConfig.getPassword());
            
            // Solo agregar client secret si está configurado
            if (keycloakAdminConfig.getClientSecret() != null && !keycloakAdminConfig.getClientSecret().isEmpty()) {
                builder.clientSecret(keycloakAdminConfig.getClientSecret());
            }
            
            keycloak = builder.build();
        }
        return keycloak;
    }

    /**
     * Crea un nuevo usuario en Keycloak con rol de CLIENTE
     * @param username Nombre de usuario
     * @param email Email del usuario
     * @param firstName Nombre
     * @param lastName Apellido
     * @param password Contraseña
     * @return ID del usuario creado en Keycloak
     * @throws RuntimeException si ocurre un error al crear el usuario
     */
    public String crearUsuario(String username, String email, String firstName, String lastName, String password) {
        return crearUsuarioConRol(username, email, firstName, lastName, password, "CLIENTE");
    }

    /**
     * Crea un nuevo usuario en Keycloak con un rol específico
     * @param username Nombre de usuario
     * @param email Email del usuario
     * @param firstName Nombre
     * @param lastName Apellido
     * @param password Contraseña
     * @param rol Rol a asignar (CLIENTE, OPERADOR, TRANSPORTISTA, ADMIN)
     * @return ID del usuario creado en Keycloak
     * @throws RuntimeException si ocurre un error al crear el usuario
     */
    public String crearUsuarioConRol(String username, String email, String firstName, String lastName, String password, String rol) {
        try {
            Keycloak keycloakInstance = getKeycloakInstance();
            // Usar el realm tpi-backend para crear usuarios, no el realm de autenticación (master)
            RealmResource realmResource = keycloakInstance.realm("tpi-backend");
            UsersResource usersResource = realmResource.users();

            // Crear representación del usuario
            UserRepresentation user = new UserRepresentation();
            user.setUsername(username);
            user.setEmail(email);
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setEnabled(true);
            user.setEmailVerified(true);

            // Configurar credenciales
            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(password);
            credential.setTemporary(false);
            user.setCredentials(Collections.singletonList(credential));

            // Crear usuario en Keycloak
            Response response = usersResource.create(user);
            
            if (response.getStatus() != 201) {
                String errorMessage = response.readEntity(String.class);
                log.error("Error al crear usuario en Keycloak. Status: {}, Error: {}", response.getStatus(), errorMessage);
                throw new RuntimeException("Error al crear usuario en Keycloak: " + errorMessage);
            }

            // Obtener el ID del usuario creado
            String userId = response.getLocation().getPath().replaceAll(".*/([^/]+)$", "$1");
            log.info("Usuario creado en Keycloak con ID: {}", userId);

            // Asignar rol especificado
            asignarRol(userId, rol);

            return userId;

        } catch (Exception e) {
            log.error("Error al crear usuario en Keycloak", e);
            throw new RuntimeException("Error al crear usuario en Keycloak: " + e.getMessage(), e);
        }
    }

    /**
     * Asigna un rol a un usuario en Keycloak
     * @param userId ID del usuario en Keycloak
     * @param roleName Nombre del rol a asignar
     */
    private void asignarRol(String userId, String roleName) {
        try {
            Keycloak keycloakInstance = getKeycloakInstance();
            // Usar el realm tpi-backend para asignar roles
            RealmResource realmResource = keycloakInstance.realm("tpi-backend");
            
            // Obtener el rol por nombre
            RoleRepresentation role = realmResource.roles().get(roleName).toRepresentation();
            
            // Asignar rol al usuario
            realmResource.users().get(userId).roles().realmLevel().add(Collections.singletonList(role));
            
            log.info("Rol {} asignado al usuario {}", roleName, userId);
        } catch (Exception e) {
            log.error("Error al asignar rol {} al usuario {}", roleName, userId, e);
            throw new RuntimeException("Error al asignar rol: " + e.getMessage(), e);
        }
    }

    /**
     * Verifica si un username ya existe en Keycloak
     * @param username Username a verificar
     * @return true si el username ya existe, false en caso contrario
     */
    public boolean existeUsername(String username) {
        try {
            Keycloak keycloakInstance = getKeycloakInstance();
            // Usar el realm tpi-backend para verificar usuarios
            RealmResource realmResource = keycloakInstance.realm("tpi-backend");
            UsersResource usersResource = realmResource.users();
            
            List<UserRepresentation> users = usersResource.search(username, true);
            return !users.isEmpty();
        } catch (Exception e) {
            log.error("Error al verificar existencia de username: {}", username, e);
            return false;
        }
    }

    /**
     * Verifica si un email ya existe en Keycloak
     * @param email Email a verificar
     * @return true si el email ya existe, false en caso contrario
     */
    public boolean existeEmail(String email) {
        try {
            Keycloak keycloakInstance = getKeycloakInstance();
            // Usar el realm tpi-backend para verificar emails
            RealmResource realmResource = keycloakInstance.realm("tpi-backend");
            UsersResource usersResource = realmResource.users();
            
            List<UserRepresentation> users = usersResource.searchByEmail(email, true);
            return !users.isEmpty();
        } catch (Exception e) {
            log.error("Error al verificar existencia de email: {}", email, e);
            return false;
        }
    }

    /**
     * Obtiene todos los usuarios del realm tpi-backend
     * @return Lista de representaciones de usuarios
     */
    public List<UserRepresentation> obtenerTodosLosUsuarios() {
        try {
            Keycloak keycloakInstance = getKeycloakInstance();
            RealmResource realmResource = keycloakInstance.realm("tpi-backend");
            UsersResource usersResource = realmResource.users();
            
            List<UserRepresentation> users = usersResource.list();
            log.info("Se obtuvieron {} usuarios del realm", users.size());
            return users;
        } catch (Exception e) {
            log.error("Error al obtener todos los usuarios", e);
            throw new RuntimeException("Error al obtener usuarios: " + e.getMessage(), e);
        }
    }

    /**
     * Obtiene los roles de un usuario específico
     * @param userId ID del usuario en Keycloak
     * @return Lista de nombres de roles
     */
    public List<String> obtenerRolesUsuario(String userId) {
        try {
            Keycloak keycloakInstance = getKeycloakInstance();
            RealmResource realmResource = keycloakInstance.realm("tpi-backend");
            
            List<RoleRepresentation> roles = realmResource.users().get(userId).roles().realmLevel().listAll();
            return roles.stream()
                    .map(RoleRepresentation::getName)
                    .filter(name -> !name.startsWith("default-") && !name.equals("uma_authorization") && !name.equals("offline_access"))
                    .toList();
        } catch (Exception e) {
            log.error("Error al obtener roles del usuario {}", userId, e);
            return Collections.emptyList();
        }
    }
}

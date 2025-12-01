package com.backend.tpi.ms_solicitudes.controllers;

import com.backend.tpi.ms_solicitudes.dto.UsuarioDTO;
import com.backend.tpi.ms_solicitudes.dto.UsuarioRegistroDTO;
import com.backend.tpi.ms_solicitudes.dto.UsuarioRegistroResponseDTO;
import com.backend.tpi.ms_solicitudes.services.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador REST para gestionar Usuarios del sistema
 * Permite al administrador crear usuarios con diferentes roles
 */
@RestController
@RequestMapping("/api/v1/usuarios")
@Tag(name = "Usuarios", description = "Gestión de usuarios del sistema (solo ADMIN)")
public class UsuarioController {

    private static final Logger logger = LoggerFactory.getLogger(UsuarioController.class);

    @Autowired
    private UsuarioService usuarioService;

    /**
     * GET /api/v1/usuarios - Obtener todos los usuarios del sistema
     * Requiere rol ADMIN
     * @return Lista de usuarios con sus datos y roles
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Obtener todos los usuarios del sistema (requiere rol ADMIN)")
    public ResponseEntity<?> obtenerTodosLosUsuarios() {
        logger.info("GET /api/v1/usuarios - Administrador obteniendo lista de usuarios");
        try {
            List<UsuarioDTO> usuarios = usuarioService.obtenerTodosLosUsuarios();
            logger.info("GET /api/v1/usuarios - Respuesta: 200 - {} usuarios encontrados", usuarios.size());
            return ResponseEntity.ok(usuarios);
        } catch (RuntimeException e) {
            logger.error("GET /api/v1/usuarios - Error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                new ErrorResponse("Error al obtener usuarios: " + e.getMessage())
            );
        }
    }

    /**
     * POST /api/v1/usuarios/registro - Registro de nuevos usuarios por el administrador
     * Requiere rol ADMIN
     * Crea usuario en Keycloak con el rol especificado
     * Si el rol es CLIENTE, también guarda datos en BD local
     * @param registroDTO Datos del usuario a registrar
     * @return Usuario creado con código 201
     */
    @PostMapping("/registro")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Registro de usuarios por el administrador (requiere rol ADMIN)")
    public ResponseEntity<?> registrarUsuario(@Valid @RequestBody UsuarioRegistroDTO registroDTO) {
        logger.info("POST /api/v1/usuarios/registro - Administrador registrando nuevo usuario: {} con rol: {}", 
                    registroDTO.getEmail(), registroDTO.getRol());
        try {
            UsuarioRegistroResponseDTO response = usuarioService.registrarUsuario(registroDTO);
            logger.info("POST /api/v1/usuarios/registro - Respuesta: 201 - Usuario registrado con rol: {}", 
                        response.getRol());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (RuntimeException e) {
            logger.error("POST /api/v1/usuarios/registro - Error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                new ErrorResponse(e.getMessage())
            );
        }
    }

    /**
     * Clase interna para respuestas de error
     */
    private static class ErrorResponse {
        private String error;

        public ErrorResponse(String error) {
            this.error = error;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }
    }
}

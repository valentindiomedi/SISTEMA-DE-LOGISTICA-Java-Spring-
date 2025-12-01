package com.backend.tpi.ms_solicitudes.controllers;

import com.backend.tpi.ms_solicitudes.dto.ClienteRegistroDTO;
import com.backend.tpi.ms_solicitudes.dto.ClienteRegistroResponseDTO;
import com.backend.tpi.ms_solicitudes.models.Cliente;
import com.backend.tpi.ms_solicitudes.services.ClienteService;
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
 * Controlador REST para gestionar Clientes
 * Permite crear, modificar, eliminar y consultar clientes del sistema
 */
@RestController
@RequestMapping("/api/v1/clientes")
@Tag(name = "Clientes", description = "Gestión de clientes")
public class ClienteController {

    private static final Logger logger = LoggerFactory.getLogger(ClienteController.class);

    @Autowired
    private ClienteService clienteService;

    /**
     * POST /api/v1/clientes/registro - Registro público de nuevos clientes
     * No requiere autenticación - Endpoint público
     * Crea usuario en Keycloak con rol CLIENTE y guarda datos en BD
     * @param registroDTO Datos del cliente a registrar
     * @return Cliente creado con código 201
     */
    @PostMapping("/registro")
    @Operation(summary = "Registro público de nuevos clientes (sin autenticación)")
    public ResponseEntity<?> registrarCliente(@Valid @RequestBody ClienteRegistroDTO registroDTO) {
        logger.info("POST /api/v1/clientes/registro - Registrando nuevo cliente: {}", registroDTO.getEmail());
        try {
            ClienteRegistroResponseDTO response = clienteService.registrarCliente(registroDTO);
            logger.info("POST /api/v1/clientes/registro - Respuesta: 201 - Cliente registrado con ID: {}", response.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (RuntimeException e) {
            logger.error("POST /api/v1/clientes/registro - Error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                new ErrorResponse(e.getMessage())
            );
        }
    }

    /**
     * GET /api/v1/clientes - Lista todos los clientes o busca por email
     * Si se proporciona parámetro email, busca un cliente específico
    * Requiere rol OPERADOR o ADMIN
     * @param email Email del cliente (opcional)
     * @return Lista de clientes o cliente específico
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('OPERADOR', 'ADMIN')")
    @Operation(summary = "Listar todos los clientes o buscar por email")
    public ResponseEntity<?> getAllClientes(@RequestParam(required = false) String email) {
        if (email != null && !email.isEmpty()) {
            logger.info("GET /api/v1/clientes - Buscando cliente por email");
            Cliente cliente = clienteService.findByEmail(email);
            logger.info("GET /api/v1/clientes - Respuesta: 200 - Cliente encontrado con ID: {}", cliente.getId());
            return ResponseEntity.ok(cliente);
        }
        logger.info("GET /api/v1/clientes - Listando todos los clientes");
        List<Cliente> clientes = clienteService.findAll();
        logger.info("GET /api/v1/clientes - Respuesta: 200 - {} clientes encontrados", clientes.size());
        return ResponseEntity.ok(clientes);
    }

    /**
     * GET /api/v1/clientes/{id} - Obtiene un cliente por ID
    * Requiere rol CLIENTE, OPERADOR o ADMIN
     * @param id ID del cliente
     * @return Cliente encontrado
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLIENTE', 'OPERADOR', 'ADMIN')")
    @Operation(summary = "Obtener cliente por ID")
    public ResponseEntity<Cliente> getClienteById(@PathVariable Long id) {
        logger.info("GET /api/v1/clientes/{} - Buscando cliente por ID", id);
        Cliente cliente = clienteService.findById(id);
        logger.info("GET /api/v1/clientes/{} - Respuesta: 200 - Cliente encontrado", id);
        return ResponseEntity.ok(cliente);
    }

    /**
     * POST /api/v1/clientes - Crea un nuevo cliente
    * Requiere rol OPERADOR o ADMIN
     * @param cliente Datos del cliente a crear
     * @return Cliente creado con código 201
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('OPERADOR', 'ADMIN')")
    @Operation(summary = "Crear nuevo cliente")
    public ResponseEntity<Cliente> createCliente(@RequestBody Cliente cliente) {
        logger.info("POST /api/v1/clientes - Creando nuevo cliente");
        Cliente nuevoCliente = clienteService.save(cliente);
        logger.info("POST /api/v1/clientes - Respuesta: 201 - Cliente creado con ID: {}", nuevoCliente.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(nuevoCliente);
    }

    /**
    * PUT /api/v1/clientes/{id} - Actualiza un cliente existente
    * Requiere rol OPERADOR o ADMIN
     * @param id ID del cliente a actualizar
     * @param cliente Datos actualizados del cliente
     * @return Cliente actualizado
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERADOR', 'ADMIN')")
    @Operation(summary = "Actualizar cliente existente")
    public ResponseEntity<Cliente> updateCliente(@PathVariable Long id, @RequestBody Cliente cliente) {
        logger.info("PUT /api/v1/clientes/{} - Actualizando cliente", id);
        Cliente clienteActualizado = clienteService.update(id, cliente);
        logger.info("PUT /api/v1/clientes/{} - Respuesta: 200 - Cliente actualizado", id);
        return ResponseEntity.ok(clienteActualizado);
    }

    /**
     * DELETE /api/v1/clientes/{id} - Elimina un cliente
     * Requiere rol ADMIN
     * @param id ID del cliente a eliminar
     * @return No Content (204)
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Eliminar cliente")
    public ResponseEntity<Void> deleteCliente(@PathVariable Long id) {
        logger.info("DELETE /api/v1/clientes/{} - Eliminando cliente", id);
        clienteService.deleteById(id);
        logger.info("DELETE /api/v1/clientes/{} - Respuesta: 204 - Cliente eliminado", id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Clase interna para respuestas de error
     */
    private static class ErrorResponse {
        private final String mensaje;

        public ErrorResponse(String mensaje) {
            this.mensaje = mensaje;
        }

        public String getMensaje() {
            return mensaje;
        }
    }
}

package com.backend.tpi.ms_solicitudes.config;

import com.backend.tpi.ms_solicitudes.models.*;
import com.backend.tpi.ms_solicitudes.repositories.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DataInitializer implements CommandLineRunner {

    private final EstadoSolicitudRepository estadoSolicitudRepository;
    private final EstadoContenedorRepository estadoContenedorRepository;
    private final ClienteRepository clienteRepository;
    private final ContenedorRepository contenedorRepository;

    public DataInitializer(EstadoSolicitudRepository estadoSolicitudRepository,
                           EstadoContenedorRepository estadoContenedorRepository,
                           ClienteRepository clienteRepository,
                           ContenedorRepository contenedorRepository) {
        this.estadoSolicitudRepository = estadoSolicitudRepository;
        this.estadoContenedorRepository = estadoContenedorRepository;
        this.clienteRepository = clienteRepository;
        this.contenedorRepository = contenedorRepository;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        // Asegurar que existan los estados canónicos (upsert por nombre)
        String[] estadosCanonicos = new String[] {"PENDIENTE", "PROGRAMADA", "EN_TRANSITO", "COMPLETADA", "CANCELADA"};
        for (String nombre : estadosCanonicos) {
            try {
                estadoSolicitudRepository.findByNombre(nombre).orElseGet(() -> {
                    EstadoSolicitud e = new EstadoSolicitud();
                    e.setNombre(nombre);
                    return estadoSolicitudRepository.save(e);
                });
            } catch (Exception ex) {
                // Registrar y continuar; evitar que el initializer deje de ejecutarse por una fila duplicada u otro problema
                System.err.println("Warning: no se pudo garantizar estado '" + nombre + "': " + ex.getMessage());
            }
        }

        // Inicializar estados de contenedor si no existen
        if (estadoContenedorRepository.count() == 0) {
            EstadoContenedor libre = new EstadoContenedor(); libre.setNombre("LIBRE");
            EstadoContenedor ocupado = new EstadoContenedor(); ocupado.setNombre("OCUPADO");
            EstadoContenedor enTransito = new EstadoContenedor(); enTransito.setNombre("EN_TRANSITO");
            EstadoContenedor enDeposito = new EstadoContenedor(); enDeposito.setNombre("EN_DEPOSITO");
            EstadoContenedor entregado = new EstadoContenedor(); entregado.setNombre("ENTREGADO");
            estadoContenedorRepository.save(libre);
            estadoContenedorRepository.save(ocupado);
            estadoContenedorRepository.save(enTransito);
            estadoContenedorRepository.save(enDeposito);
            estadoContenedorRepository.save(entregado);
        }

        // Crear un cliente de ejemplo para pruebas si no existen clientes
        if (clienteRepository.count() == 0) {
            Cliente c = new Cliente();
            c.setNombre("Cliente Demo");
            c.setEmail("demo@cliente.local");
            c.setTelefono("+000000000");
            clienteRepository.save(c);

            // Crear un contenedor asociado para pruebas
            Contenedor cont = new Contenedor();
            cont.setClienteId(c.getId());
            cont.setPeso(new java.math.BigDecimal("100.0"));
            cont.setVolumen(new java.math.BigDecimal("1.5"));
            // asignar estado 'LIBRE' si existe
            estadoContenedorRepository.findByNombre("LIBRE").ifPresent(cont::setEstado);
            contenedorRepository.save(cont);
        }
    }
}

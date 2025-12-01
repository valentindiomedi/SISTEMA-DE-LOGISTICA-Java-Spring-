package com.backend.tpi.ms_solicitudes.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestMicroservicioConfig {

    // Cliente para ms-gestion-calculos (calculos)
    @Bean
    public RestClient calculosClient(@Value("${app.calculos.base-url:http://localhost:8081}") String baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    // Cliente para ms-rutas-transportistas (rutas)
    @Bean
    public RestClient rutasClient(@Value("${app.rutas.base-url:http://localhost:8082}") String baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

        // Proveer un RestTemplate simple para inyección en servicios que lo requieran
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

}

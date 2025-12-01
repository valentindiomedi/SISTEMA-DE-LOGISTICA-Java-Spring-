package com.backend.tpi.ms_gestion_calculos.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClient;

@Configuration
public class RestMicroservicioConfig {

    @Bean
    public RestClient rutasClient(@Value("${app.rutas.base-url:http://localhost:8082}") String baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    @Bean
    public RestClient solicitudesClient(@Value("${app.solicitudes.base-url:http://localhost:8083}") String baseUrl) {
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

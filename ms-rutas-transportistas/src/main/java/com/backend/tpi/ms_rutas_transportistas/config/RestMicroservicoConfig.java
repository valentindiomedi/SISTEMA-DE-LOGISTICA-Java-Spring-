package com.backend.tpi.ms_rutas_transportistas.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;
// No HttpComponentsClientHttpRequestFactory usage to avoid version mismatches

@Configuration
public class RestMicroservicoConfig {

    // Cliente para ms-solicitudes. Configurar en application.yml como app.solicitudes.base-url
    @Bean
    public RestClient solicitudesClient(@Value("${app.solicitudes.base-url:http://localhost:8083}") String baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    // Cliente para ms-gestion-calculos. Configurar en application.yml como app.calculos.base-url
    // Bean principal nombrado "calculosClient" para inyección por nombre
    @Bean("calculosClient")
    public RestClient calculosClient(@Value("${app.calculos.base-url:http://localhost:8081}") String baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    // RestTemplate bean para compatibilidad con código legacy
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    // Proveer un RestTemplate simple para inyección en servicios que lo requieran
}

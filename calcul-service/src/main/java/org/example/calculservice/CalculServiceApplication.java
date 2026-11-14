package org.example.calculservice;

import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.List;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class CalculServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CalculServiceApplication.class, args);
    }

    @Bean
    OpenAPI openAPI() {
        // Adresse relative : Swagger UI la résout par rapport à la page ouverte,
        // donc http://localhost:8888/calcul-service quand on passe par la passerelle
        return new OpenAPI().servers(List.of(new Server().url("/calcul-service")));
    }

}
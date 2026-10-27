package com.pedidos360.common;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
  @Bean
  OpenAPI serviceDocumentation(@Value("${spring.application.name:pedidos360}") String service) {
    return new OpenAPI()
        .info(new Info().title("Pedidos360 - " + service).version("0.2.0")
            .description("API del caso Pedidos360. Las operaciones protegidas requieren access token de Entra y el rol correspondiente."))
        .components(new Components().addSecuritySchemes("EntraBearer",
            new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
        .addSecurityItem(new SecurityRequirement().addList("EntraBearer"));
  }
}

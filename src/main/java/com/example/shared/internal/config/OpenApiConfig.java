package com.example.shared.internal.config;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "Bearer Authentication";

    @Bean
    public OpenAPI springSettingsOpenApi(ObjectProvider<BuildProperties> buildPropertiesProvider) {
        String version = Optional.ofNullable(buildPropertiesProvider.getIfAvailable())
                .map(BuildProperties::getVersion)
                .filter(value -> !value.isBlank())
                .orElse("local");

        SecurityScheme securityScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");

        return new OpenAPI()
                .info(new Info()
                        .title("Spring Settings API")
                        .description("Spring Modulith project settings template API")
                        .version(version))
                .servers(List.of(new Server().url("/").description("Current environment")))
                .schemaRequirement(BEARER_AUTH, securityScheme);
    }
}

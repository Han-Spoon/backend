package com.hanspoon.backend_api.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI hanSpoonOpenApi() {
        return new OpenAPI()
                .info(apiInfo())
                .components(new Components().addSecuritySchemes(BEARER_AUTH, bearerSecurityScheme()))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }

    private Info apiInfo() {
        return new Info()
                .title("Han-Spoon Backend API")
                .description("Foreign tourist service API for Han-Spoon running on Azure Container Apps.")
                .version("v1")
                .contact(new Contact().name("Han-Spoon Backend Team").email("backend@han-spoon.com"))
                .license(new License().name("Private").url("https://han-spoon.com"));
    }

    private SecurityScheme bearerSecurityScheme() {
        return new SecurityScheme()
                .name("Authorization")
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .in(SecurityScheme.In.HEADER)
                .description("Input only the JWT access token. Swagger UI will send it as: Bearer {token}");
    }
}

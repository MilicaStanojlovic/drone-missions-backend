package com.project.drone_missions.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI/Swagger metadata. Declares the {@code bearer} (JWT) security scheme so the
 * Swagger UI <em>Authorize</em> button lets you exercise the secured endpoints — paste the
 * token returned in login's {@code Authorization} response header. Everything else about
 * the docs is springdoc's zero-config default.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI droneMissionsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Drone Missions API")
                        .description("Mission marketplace and stateless JWT authentication.")
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}

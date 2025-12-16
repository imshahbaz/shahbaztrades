package com.shahbaz.trades.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class SwaggerConfig {

        @Bean
        public OpenAPI openApi() {
                return new OpenAPI()
                                .addSecurityItem(new SecurityRequirement().addList("ApiKeyAuth"))
                                .components(
                                                new Components().securitySchemes(Map.of(
                                                                "ApiKeyAuth",
                                                                new SecurityScheme()
                                                                                .name("X-API-KEY")
                                                                                .type(SecurityScheme.Type.APIKEY)
                                                                                .in(SecurityScheme.In.HEADER)
                                                                                .description("Enter API key for swagger request"))));
        }

}

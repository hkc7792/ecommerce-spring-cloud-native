package com.ecommerce.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(title = "E-Commerce API", version = "1.0.0", description = "REST endpoints for order payment inventory and user services"))
public class OpenApiConfig {
}

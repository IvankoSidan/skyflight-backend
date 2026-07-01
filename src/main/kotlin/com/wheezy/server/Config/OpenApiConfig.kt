package com.wheezy.server.Config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun customOpenAPI(): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("SkyFlight API")
                    .version("2.0.0")
                    .description("""
                        SkyFlight Flight Booking Platform API
                        
                        ## Features:
                        - User authentication (JWT)
                        - Flight search and booking with seat locking
                        - Payment processing with Stripe
                        - Loyalty points system with hold mechanism
                        - Reviews and ratings
                        - Referral program
                        - Real-time WebSocket notifications
                        - Redis caching
                        - Rate limiting
                    """.trimIndent())
                    .contact(
                        Contact()
                            .name("SkyFlight Support")
                            .email("support@skyflightbooking.ru")
                            .url("https://skyflightbooking.ru")
                    )
                    .license(
                        License()
                            .name("Proprietary")
                            .url("https://skyflightbooking.ru")
                    )
            )
            .addServersItem(Server().url("https://skyflightbooking.ru").description("Production Server"))
            .addServersItem(Server().url("http://localhost:8080").description("Local Development"))
            .addSecurityItem(SecurityRequirement().addList("BearerAuth"))
            .components(
                Components()
                    .addSecuritySchemes(
                        "BearerAuth",
                        SecurityScheme()
                            .name("BearerAuth")
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                            .description("Enter JWT token: `Bearer {token}`")
                    )
            )
    }
}
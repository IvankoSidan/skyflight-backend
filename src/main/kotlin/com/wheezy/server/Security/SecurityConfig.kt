package com.wheezy.server.Security

import com.wheezy.server.Repository.UserDetailsLoader
import com.wheezy.server.Service.CustomOAuth2UserService
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.AuthenticationException
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import java.io.IOException
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
@EnableWebSecurity
@EnableTransactionManagement
@EnableMethodSecurity(prePostEnabled = true)
class SecurityConfig(
    private val jwtUtil: JwtUtil,
    private val userDetailsLoader: UserDetailsLoader,
    private val customOAuth2UserService: CustomOAuth2UserService,
    @Value("\${cors.allowed-origins}") private val allowedOriginsProp: String
) : WebMvcConfigurer {

    companion object {
        private val PUBLIC_ENDPOINTS = arrayOf(
            "/",
            "/api/auth/**",
            "/login/oauth2/**",
            "/oauth2/**",
            "/api/logo/**",
            "/static/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/api/stripe/webhook",
            "/ws/**",
            "/ws",
            "/socket/**",
            "/api/health"
        )

        private val AUTHENTICATED_ENDPOINTS = arrayOf(
            "/api/flights/**",
            "/uploads/**",
            "/api/payments/**",
            "/api/bookings/**",
            "/api/notifications/**",
            "/api/payments/sheet",
            "/api/auth/me",
            "/api/bookings/my"
        )
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers(*PUBLIC_ENDPOINTS).permitAll()
                    .requestMatchers(*AUTHENTICATED_ENDPOINTS).authenticated()
                    .anyRequest().authenticated()
            }
            .oauth2Login {
                it.userInfoEndpoint { endpoint ->
                    endpoint.userService(customOAuth2UserService)
                }.successHandler { _, response, auth ->
                    val user = auth.principal as OAuth2User
                    val email = user.attributes["email"] as String
                    val token = jwtUtil.generateToken(email)
                    response.sendRedirect("/api/auth/oauth2/success?token=$token")
                }
            }
            .exceptionHandling {
                it.authenticationEntryPoint(JwtAuthenticationEntryPoint())
                    .accessDeniedHandler(JwtAccessDeniedHandler())
            }
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val allowedOrigins = allowedOriginsProp.split(",").map { it.trim() }
        val configuration = CorsConfiguration().apply {
            setAllowedOrigins(allowedOrigins)
            allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
            allowedHeaders = listOf("Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin", "X-CSRF-Token")
            exposedHeaders = listOf("Authorization", "X-Custom-Token")
            allowCredentials = true
            maxAge = 3600L
        }

        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", configuration)
        }
    }

    override fun addCorsMappings(registry: CorsRegistry) {
        val origins = allowedOriginsProp.split(",").map { it.trim() }.toTypedArray()
        registry.addMapping("/**")
            .allowedOrigins(*origins)
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
            .allowedHeaders("*")
            .allowCredentials(true)
    }

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry.addResourceHandler("/uploads/**")
            .addResourceLocations("file:./uploads/")
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun authenticationManager(config: AuthenticationConfiguration): AuthenticationManager =
        config.authenticationManager

    @Bean
    fun jwtAuthenticationFilter(): JwtAuthenticationFilter =
        JwtAuthenticationFilter(jwtUtil, userDetailsLoader)

    class JwtAuthenticationEntryPoint : AuthenticationEntryPoint {
        override fun commence(req: HttpServletRequest, res: HttpServletResponse, ex: AuthenticationException) {
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized")
        }
    }

    class JwtAccessDeniedHandler : AccessDeniedHandler {
        override fun handle(req: HttpServletRequest?, res: HttpServletResponse?, ex: AccessDeniedException?) {
            res?.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied")
        }
    }
}

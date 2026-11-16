package com.securepay.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.securepay.dto.ErrorResponse;
import com.securepay.idempotency.IdempotencyFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final ApiKeyAuthenticationFilter apiKeyAuthFilter;
    private final RateLimitingFilter rateLimitingFilter;
    private final IdempotencyFilter idempotencyFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter,
                          ApiKeyAuthenticationFilter apiKeyAuthFilter,
                          RateLimitingFilter rateLimitingFilter,
                          IdempotencyFilter idempotencyFilter,
                          ObjectMapper objectMapper) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.apiKeyAuthFilter = apiKeyAuthFilter;
        this.rateLimitingFilter = rateLimitingFilter;
        this.idempotencyFilter = idempotencyFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    ErrorResponse error = new ErrorResponse(
                            HttpStatus.UNAUTHORIZED.value(),
                            "Unauthorized",
                            authException.getMessage() != null ? authException.getMessage() : "Full authentication is required to access this resource",
                            request.getRequestURI()
                    );
                    response.getWriter().write(objectMapper.writeValueAsString(error));
                }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/**").permitAll()
                        .requestMatchers("/actuator/**", "/error").permitAll()
                        .requestMatchers("/api/v1/api-keys/**").hasRole("MERCHANT_ADMIN")
                        .requestMatchers("/api/v1/audit-logs/**").hasRole("MERCHANT_ADMIN")
                        .requestMatchers("/api/v1/payments/**").hasAnyRole("MERCHANT_ADMIN", "MERCHANT_OPERATOR", "MERCHANT_API")
                        .anyRequest().authenticated()
                )
                // Filter ordering:
                // 1. JWT filter
                // 2. API Key filter
                // 3. Rate limiting filter
                // 4. Idempotency filter
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(apiKeyAuthFilter, JwtAuthenticationFilter.class)
                .addFilterAfter(rateLimitingFilter, ApiKeyAuthenticationFilter.class)
                .addFilterAfter(idempotencyFilter, RateLimitingFilter.class);

        return http.build();
    }
}

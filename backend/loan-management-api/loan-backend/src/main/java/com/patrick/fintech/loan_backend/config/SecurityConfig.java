package com.patrick.fintech.loan_backend.config;

import com.patrick.fintech.loan_backend.repository.OrganizationRepository;
import com.patrick.fintech.loan_backend.security.JwtAuthFilter;
import com.patrick.fintech.loan_backend.security.RegulatoryApiKeyAuthFilter;
import com.patrick.fintech.loan_backend.security.TenantResolutionFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtFilter;
    private final RegulatoryApiKeyAuthFilter regulatoryApiKeyAuthFilter;
    private final TenantResolutionFilter tenantResolutionFilter;
    private final OrganizationRepository orgRepo;

    /**
     * Example Render environment variable:
     *
     * CORS_ORIGINS=https://fintech01-aydw.vercel.app,http://localhost:3000
     *
     * Do NOT put a trailing slash on origins.
     */
    @Value("${app.cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
            .cors(cors -> cors.configurationSource(corsSource()))

            .csrf(csrf -> csrf.disable())

            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            .exceptionHandling(exception ->
                exception.authenticationEntryPoint((request, response, authException) -> {

                    response.setStatus(
                        jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED
                    );

                    response.setContentType("application/json");

                    response.getWriter().write(
                        "{\"success\":false,\"error\":\"Your session has expired or is no longer valid. Please log in again.\"}"
                    );
                })
            )

            .authorizeHttpRequests(auth -> auth

                /*
                 * Public endpoints
                 */
                .requestMatchers(
                    "/api/auth/**",
                    "/api/public/**",
                    "/actuator/health",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/api-docs/**",
                    "/h2-console/**"
                ).permitAll()

                /*
                 * Everything else requires authentication.
                 */
                .anyRequest().authenticated()
            )

            .headers(headers ->
                headers.frameOptions(frame ->
                    frame.sameOrigin()
                )
            )

            /*
             * Tenant resolution happens before authentication.
             *
             * This allows anonymous public website requests such as:
             *
             * growthfinance.rw/api/public/...
             * abcsacco.rw/api/public/...
             */
            .addFilterBefore(
                tenantResolutionFilter,
                UsernamePasswordAuthenticationFilter.class
            )

            /*
             * JWT authentication.
             */
            .addFilterBefore(
                jwtFilter,
                UsernamePasswordAuthenticationFilter.class
            )

            /*
             * Regulatory API key authentication.
             */
            .addFilterBefore(
                regulatoryApiKeyAuthFilter,
                UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }

    /**
     * CORS configuration.
     *
     * There are two types of allowed origins:
     *
     * 1. Static application origins
     *    - Vercel frontend
     *    - localhost development
     *
     * 2. Verified customer domains
     *    - growthfinance.rw
     *    - abcsacco.rw
     *    - etc.
     */
    @Bean
    public CorsConfigurationSource corsSource() {

        final List<String> staticOrigins = Arrays.stream(
                allowedOrigins.split(",")
            )
            .map(String::trim)
            .filter(origin -> !origin.isBlank())
            .map(this::removeTrailingSlash)
            .toList();

        return request -> {

            CorsConfiguration config = new CorsConfiguration();

            config.setAllowedMethods(List.of(
                "GET",
                "POST",
                "PUT",
                "PATCH",
                "DELETE",
                "OPTIONS"
            ));

            config.setAllowedHeaders(List.of("*"));

            config.setAllowCredentials(true);

            config.setMaxAge(3600L);

            String origin = request.getHeader("Origin");

            /*
             * No Origin means this is probably:
             *
             * - curl
             * - Postman
             * - server-to-server request
             * - browser navigation
             *
             * Don't apply browser CORS restrictions.
             */
            if (origin == null || origin.isBlank()) {
                return config;
            }

            origin = removeTrailingSlash(origin);

            /*
             * 1. Main Vercel application / local development.
             */
            if (staticOrigins.contains(origin)) {

                config.setAllowedOrigins(
                    List.of(origin)
                );

                return config;
            }

            /*
             * 2. Customer's own domain.
             *
             * Example:
             *
             * Origin:
             * https://growthfinance.rw
             *
             * Database:
             * growthfinance.rw
             *
             * Domain must be verified before being accepted.
             */
            if (matchesVerifiedOrganization(origin)) {

                config.setAllowedOrigins(
                    List.of(origin)
                );

                return config;
            }

            /*
             * Unknown origin.
             *
             * Don't return an allowed origin.
             */
            return config;
        };
    }

    /**
     * Checks whether the browser origin belongs to a verified
     * customer organization.
     */
    private boolean matchesVerifiedOrganization(String origin) {

        try {

            URI uri = URI.create(origin);

            String host = uri.getHost();

            if (host == null || host.isBlank()) {
                return false;
            }

            host = host.toLowerCase().trim();

            if (host.startsWith("www.")) {
                host = host.substring(4);
            }

            return orgRepo
                .findByDomainIgnoreCaseAndDomainVerifiedTrue(host)
                .isPresent();

        } catch (Exception e) {

            return false;
        }
    }

    /**
     * Removes a trailing slash.
     *
     * https://fintech01-aydw.vercel.app/
     *
     * becomes:
     *
     * https://fintech01-aydw.vercel.app
     */
    private String removeTrailingSlash(String value) {

        if (value == null) {
            return null;
        }

        value = value.trim();

        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }

        return value;
    }

    @Bean
    public AuthenticationManager authManager(
            AuthenticationConfiguration configuration
    ) throws Exception {

        return configuration.getAuthenticationManager();
    }
}
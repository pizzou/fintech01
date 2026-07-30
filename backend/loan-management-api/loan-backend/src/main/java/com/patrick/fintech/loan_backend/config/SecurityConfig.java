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

    @Value("${app.cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http
    ) throws Exception {

        http

            .cors(cors ->
                    cors.configurationSource(
                            corsSource()
                    )
            )

            .csrf(csrf ->
                    csrf.disable()
            )

            .sessionManagement(session ->
                    session.sessionCreationPolicy(
                            SessionCreationPolicy.STATELESS
                    )
            )

            .exceptionHandling(exception ->
                    exception.authenticationEntryPoint(
                            (request, response, authException) -> {

                                response.setStatus(
                                        401
                                );

                                response.setContentType(
                                        "application/json"
                                );

                                response.getWriter()
                                        .write(
                                                "{\"success\":false,\"error\":\"Unauthorized.\"}"
                                        );
                            }
                    )
            )

            .authorizeHttpRequests(auth -> auth

                    .requestMatchers(
                            "/api/auth/**",
                            "/h2-console/**",
                            "/swagger-ui/**",
                            "/swagger-ui.html",
                            "/api-docs/**",
                            "/actuator/health",
                            "/api/public/**"
                    )
                    .permitAll()

                    .anyRequest()
                    .authenticated()
            )

            .headers(headers ->
                    headers.frameOptions(
                            frame -> frame.sameOrigin()
                    )
            )

            /*
             * MUST execute before JWT authentication.
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
             * Regulatory API authentication.
             */
            .addFilterBefore(
                    regulatoryApiKeyAuthFilter,
                    UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsSource() {

        return request -> {

            CorsConfiguration config =
                    new CorsConfiguration();

            config.setAllowedMethods(
                    List.of(
                            "GET",
                            "POST",
                            "PUT",
                            "PATCH",
                            "DELETE",
                            "OPTIONS"
                    )
            );

            config.setAllowedHeaders(
                    List.of("*")
            );

            config.setExposedHeaders(
                    List.of(
                            "Authorization",
                            "Content-Type"
                    )
            );

            config.setAllowCredentials(true);

            String origin =
                    request.getHeader("Origin");

            if (origin == null
                    || origin.isBlank()) {

                return config;
            }

            if (isAllowedStaticOrigin(origin)
                    || isVerifiedOrganizationOrigin(origin)) {

                config.setAllowedOrigins(
                        List.of(origin)
                );

                return config;
            }

            return new CorsConfiguration();
        };
    }

    private boolean isAllowedStaticOrigin(
            String origin
    ) {

        if (allowedOrigins == null
                || allowedOrigins.isBlank()) {

            return false;
        }

        String normalizedOrigin =
                normalizeOrigin(origin);

        for (String configured :
                allowedOrigins.split(",")) {

            if (normalizedOrigin.equalsIgnoreCase(
                    normalizeOrigin(configured)
            )) {

                return true;
            }
        }

        return false;
    }

    private boolean isVerifiedOrganizationOrigin(
            String origin
    ) {

        try {

            URI uri =
                    URI.create(origin);

            String host =
                    uri.getHost();

            if (host == null
                    || host.isBlank()) {

                return false;
            }

            host = host.toLowerCase();

            if (host.startsWith("www.")) {
                host = host.substring(4);
            }

            return orgRepo
                    .findByDomainIgnoreCaseAndDomainVerifiedTrue(
                            host
                    )
                    .isPresent();

        } catch (Exception e) {

            return false;
        }
    }

    private String normalizeOrigin(
            String origin
    ) {

        if (origin == null) {
            return "";
        }

        String value =
                origin.trim();

        while (value.endsWith("/")) {
            value =
                    value.substring(
                            0,
                            value.length() - 1
                    );
        }

        try {

            URI uri =
                    URI.create(value);

            String scheme =
                    uri.getScheme();

            String host =
                    uri.getHost();

            if (scheme == null
                    || host == null) {

                return value.toLowerCase();
            }

            host =
                    host.toLowerCase();

            if (host.startsWith("www.")) {
                host =
                        host.substring(4);
            }

            int port =
                    uri.getPort();

            if (port > 0) {

                return scheme.toLowerCase()
                        + "://"
                        + host
                        + ":"
                        + port;
            }

            return scheme.toLowerCase()
                    + "://"
                    + host;

        } catch (Exception e) {

            return value.toLowerCase();
        }
    }

    @Bean
    public AuthenticationManager authManager(
            AuthenticationConfiguration configuration
    ) throws Exception {

        return configuration
                .getAuthenticationManager();
    }
}
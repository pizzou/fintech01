package com.patrick.fintech.loan_backend.config;

import com.patrick.fintech.loan_backend.repository.OrganizationRepository;
import com.patrick.fintech.loan_backend.security.JwtAuthFilter;
import com.patrick.fintech.loan_backend.security.RegulatoryApiKeyAuthFilter;
import com.patrick.fintech.loan_backend.security.TenantResolutionFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;
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
     * Static list stays for things that AREN'T customer tenant domains: your
     * local dev server, a staging URL, and (once you build it) the separate
     * admin.loansaas.com super-admin app.
     *
     * Every real tenant domain (growthfinance.rw, abcsacco.rw, ...) is
     * checked dynamically against Organization.domain instead — see
     * corsSource() below — so onboarding a new customer domain never
     * requires touching this list or redeploying.
     */
    @Value("${app.cors.allowed-origins:https://fintech01-cy17.vercel.app/}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(c -> c.configurationSource(corsSource()))
            .csrf(c -> c.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e.authenticationEntryPoint((request, response, authException) -> {
                response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write(
                    "{\"success\":false,\"error\":\"Your session has expired or is no longer valid. Please log in again.\"}");
            }))
            .authorizeHttpRequests(a -> a
                .requestMatchers(
                    "/api/auth/**",
                    "/h2-console/**",
                    "/swagger-ui/**", "/swagger-ui.html",
                    "/api-docs/**",
                    "/actuator/health",
                    "/api/public/**"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .headers(h -> h.frameOptions(f -> f.sameOrigin()))
            // Resolves the tenant from the request's X-Tenant-Domain/Host for every
            // request, before any auth decision is made — cheap, side-effect-free
            // lookup that anonymous /api/public/** endpoints rely on.
            .addFilterBefore(tenantResolutionFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            // Only engages for /api/regulatory/external/** (see RegulatoryApiKeyAuthFilter's
            // shouldNotFilter) — everything else keeps authenticating via jwtFilter exactly as before.
            .addFilterBefore(regulatoryApiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Per-request CORS decision instead of a fixed origin list: a browser
     * request from https://www.growthfinance.rw is allowed if and only if
     * some Organization.domain equals "growthfinance.rw" (www-stripped) —
     * OR the origin is in the static allowedOrigins list above. Onboarding
     * ABC SACCO's domain via the super-admin portal is enough to make their
     * site work; nothing here needs to change.
     */
    @Bean
    public CorsConfigurationSource corsSource() {
        List<String> staticOrigins = List.of(allowedOrigins.split(","));

        return request -> {
            CorsConfiguration cfg = new CorsConfiguration();
            cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
            cfg.setAllowedHeaders(List.of("*"));
            cfg.setAllowCredentials(true);

            String origin = request.getHeader("Origin");
            if (origin != null && (staticOrigins.contains(origin) || matchesKnownOrganization(origin))) {
                cfg.setAllowedOrigins(List.of(origin));
                return cfg;
            }
            // No Origin header (server-to-server call) or origin not recognized —
            // Spring Security's CORS handling simply won't add the allow-origin
            // header, which browsers treat as a block. Non-browser clients are
            // unaffected either way.
            return null;
        };
    }

    private boolean matchesKnownOrganization(String origin) {
        try {
            String host = java.net.URI.create(origin).getHost();
            if (host == null) return false;
            host = host.toLowerCase();
            if (host.startsWith("www.")) host = host.substring(4);
            return orgRepo.findByDomainIgnoreCaseAndDomainVerifiedTrue(host).isPresent();
        } catch (Exception e) {
            return false;
        }
    }

    @Bean
    public AuthenticationManager authManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }
}
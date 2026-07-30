package com.patrick.fintech.loan_backend.security;

import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.repository.OrganizationRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class TenantResolutionFilter extends OncePerRequestFilter {

    private static final String TENANT_KEY_HEADER = "X-Tenant-Key";
    private static final String TENANT_DOMAIN_HEADER = "X-Tenant-Domain";

    private final OrganizationRepository organizationRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        TenantContext.clear();

        try {

            String tenantKey = request.getHeader(TENANT_KEY_HEADER);

            String tenantDomain = request.getHeader(TENANT_DOMAIN_HEADER);

            String origin = request.getHeader("Origin");

            log.info(
                "[TENANT] {} {} | TenantKey={} | TenantDomain={} | Origin={} | Host={}",
                request.getMethod(),
                request.getRequestURI(),
                tenantKey,
                tenantDomain,
                origin,
                request.getHeader("Host")
            );

            /*
             * 1. Explicit tenant key has highest priority.
             *
             * This is what your Vercel deployments should use.
             */
            if (tenantKey != null && !tenantKey.isBlank()) {

                Optional<Organization> organization =
                    organizationRepository.findByTenantKeyIgnoreCase(
                        tenantKey.trim()
                    );

                if (organization.isPresent()) {

                    Organization org = organization.get();

                    TenantContext.set(org);

                    log.info(
                        "[TENANT] Resolved '{}' (id={}) using X-Tenant-Key={}",
                        org.getName(),
                        org.getId(),
                        tenantKey
                    );

                    filterChain.doFilter(request, response);
                    return;
                }

                log.warn(
                    "[TENANT] Unknown tenant key: {}",
                    tenantKey
                );

                sendUnknownTenant(response);
                return;
            }

            /*
             * 2. Explicit tenant domain.
             *
             * Useful later when you have:
             *
             * https://growthfinance.rw
             * https://nobleloansolutions.rw
             */
            if (tenantDomain != null && !tenantDomain.isBlank()) {

                String normalizedDomain =
                    normalizeDomain(tenantDomain);

                Optional<Organization> organization =
                    organizationRepository.findByDomainIgnoreCase(
                        normalizedDomain
                    );

                if (organization.isPresent()) {

                    Organization org = organization.get();

                    TenantContext.set(org);

                    log.info(
                        "[TENANT] Resolved '{}' (id={}) using domain={}",
                        org.getName(),
                        org.getId(),
                        normalizedDomain
                    );

                    filterChain.doFilter(request, response);
                    return;
                }

                log.warn(
                    "[TENANT] Unknown tenant domain: {}",
                    normalizedDomain
                );

                sendUnknownTenant(response);
                return;
            }

            /*
             * 3. Try Origin ONLY when it represents an actual
             * customer domain.
             *
             * DO NOT treat *.vercel.app as a tenant.
             */
            String originDomain = extractOriginDomain(origin);

            if (originDomain != null
                    && !isPlatformFrontend(originDomain)) {

                Optional<Organization> organization =
                    organizationRepository.findByDomainIgnoreCase(
                        originDomain
                    );

                if (organization.isPresent()) {

                    Organization org = organization.get();

                    TenantContext.set(org);

                    log.info(
                        "[TENANT] Resolved '{}' from Origin={}",
                        org.getName(),
                        originDomain
                    );

                    filterChain.doFilter(request, response);
                    return;
                }
            }

            /*
             * 4. No tenant.
             *
             * Some endpoints are platform-level/public.
             */
            log.debug(
                "[TENANT] No tenant resolved for {} {}",
                request.getMethod(),
                request.getRequestURI()
            );

            filterChain.doFilter(request, response);

        } finally {

            TenantContext.clear();
        }
    }

    private String extractOriginDomain(String origin) {

        if (origin == null || origin.isBlank()) {
            return null;
        }

        try {

            URI uri = URI.create(origin.trim());

            return normalizeDomain(uri.getHost());

        } catch (Exception e) {

            log.warn(
                "[TENANT] Could not parse Origin={}",
                origin
            );

            return null;
        }
    }

    private String normalizeDomain(String domain) {

        if (domain == null || domain.isBlank()) {
            return null;
        }

        String result = domain.trim().toLowerCase();

        if (result.startsWith("http://")
                || result.startsWith("https://")) {

            try {

                URI uri = URI.create(result);

                result = uri.getHost();

            } catch (Exception e) {

                return null;
            }
        }

        if (result == null || result.isBlank()) {
            return null;
        }

        int colon = result.indexOf(':');

        if (colon > -1) {
            result = result.substring(0, colon);
        }

        if (result.startsWith("www.")) {
            result = result.substring(4);
        }

        while (result.endsWith(".")) {
            result = result.substring(0, result.length() - 1);
        }

        return result;
    }

    private boolean isPlatformFrontend(String domain) {

        if (domain == null) {
            return true;
        }

        String normalized = domain.toLowerCase();

        /*
         * Your platform frontend.
         */
        if (normalized.equals("fintech01-aydw.vercel.app")) {
            return true;
        }

        /*
         * Your Noble frontend.
         *
         * IMPORTANT:
         * This is still not used as tenant identity.
         * Tenant identity comes from X-Tenant-Key.
         */
        if (normalized.equals("nobleloan-fev7-one.vercel.app")) {
            return true;
        }

        /*
         * Any Vercel deployment should not automatically
         * become a tenant.
         */
        if (normalized.endsWith(".vercel.app")) {
            return true;
        }

        /*
         * Backend infrastructure.
         */
        if (normalized.equals("fintech01.onrender.com")) {
            return true;
        }

        if (normalized.equals("localhost")
                || normalized.equals("127.0.0.1")) {
            return true;
        }

        return false;
    }

    private void sendUnknownTenant(HttpServletResponse response)
            throws IOException {

        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);

        response.setContentType("application/json");

        response.getWriter().write(
            "{\"success\":false,\"error\":\"Unknown organization.\"}"
        );
    }
}
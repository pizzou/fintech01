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

    private final OrganizationRepository organizationRepository;

    private static final String TENANT_HEADER = "X-Tenant-Domain";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        TenantContext.clear();

        try {

            String tenantDomain = resolveTenantDomain(request);

            log.info(
                    "[TENANT] {} {} | X-Tenant-Domain={} | Origin={} | Host={} | ResolvedTenant={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    request.getHeader(TENANT_HEADER),
                    request.getHeader("Origin"),
                    request.getHeader("Host"),
                    tenantDomain
            );

            /*
             * No tenant supplied.
             *
             * This is NOT automatically an error.
             *
             * Some endpoints are platform-level endpoints and do not
             * require tenant resolution.
             */
            if (tenantDomain == null || tenantDomain.isBlank()) {

                log.debug(
                        "[TENANT] No tenant domain supplied for {} {}",
                        request.getMethod(),
                        request.getRequestURI()
                );

                filterChain.doFilter(request, response);
                return;
            }

            /*
             * Ignore infrastructure/platform hosts.
             *
             * The backend hostname is NOT itself a tenant.
             */
            if (isInfrastructureDomain(tenantDomain)) {

                log.debug(
                        "[TENANT] Infrastructure domain '{}' - skipping tenant resolution",
                        tenantDomain
                );

                filterChain.doFilter(request, response);
                return;
            }

            /*
             * Resolve tenant.
             */
            Optional<Organization> organization =
                    organizationRepository.findByDomainIgnoreCase(tenantDomain);

            if (organization.isPresent()) {

                Organization org = organization.get();

                TenantContext.set(org);

                log.info(
                        "[TENANT] Resolved organization '{}' (id={}) from domain '{}'",
                        org.getName(),
                        org.getId(),
                        tenantDomain
                );

            } else {

                /*
                 * Do not silently assign another tenant.
                 *
                 * The request continues without a tenant.
                 * Tenant-aware services/security should reject access
                 * when a tenant is mandatory.
                 */
                log.warn(
                        "[TENANT] No organization found for tenant domain '{}'",
                        tenantDomain
                );
            }

            filterChain.doFilter(request, response);

        } finally {

            /*
             * ThreadLocal cleanup is mandatory.
             */
            TenantContext.clear();
        }
    }

    /**
     * Resolve the tenant identity.
     *
     * Priority:
     *
     * 1. X-Tenant-Domain
     * 2. Origin
     *
     * We deliberately DO NOT use the backend hostname as the tenant.
     */
    private String resolveTenantDomain(HttpServletRequest request) {

        /*
         * 1. Explicit tenant header.
         *
         * Example:
         *
         * X-Tenant-Domain: growthfinance.rw
         */
        String domain = request.getHeader(TENANT_HEADER);

        if (domain != null && !domain.isBlank()) {
            return normalizeDomain(domain);
        }

        /*
         * 2. Browser Origin.
         *
         * Example:
         *
         * Origin: https://growthfinance.rw
         */
        String origin = request.getHeader("Origin");

        if (origin != null && !origin.isBlank()) {

            String originDomain = extractDomainFromOrigin(origin);

            if (originDomain != null && !originDomain.isBlank()) {
                return normalizeDomain(originDomain);
            }
        }

        /*
         * IMPORTANT:
         *
         * Do NOT:
         *
         * return request.getServerName();
         *
         * because request.getServerName() will normally be:
         *
         * fintech01.onrender.com
         *
         * That is your API host, not a tenant.
         */
        return null;
    }

    /**
     * Extract hostname from Origin.
     *
     * Example:
     *
     * https://growthfinance.rw
     *
     * -> growthfinance.rw
     */
    private String extractDomainFromOrigin(String origin) {

        if (origin == null || origin.isBlank()) {
            return null;
        }

        try {

            URI uri = URI.create(origin.trim());

            return uri.getHost();

        } catch (Exception e) {

            log.warn(
                    "[TENANT] Could not parse Origin '{}'",
                    origin
            );

            return null;
        }
    }

    /**
     * Normalize all possible representations of a domain.
     *
     * Examples:
     *
     * https://growthfinance.rw
     * http://growthfinance.rw/
     * growthfinance.rw
     * www.growthfinance.rw
     * growthfinance.rw:443
     *
     * -> growthfinance.rw
     */
    private String normalizeDomain(String domain) {

        if (domain == null || domain.isBlank()) {
            return null;
        }

        String result = domain.trim().toLowerCase();

        /*
         * Full URL.
         */
        if (result.startsWith("http://") ||
                result.startsWith("https://")) {

            try {

                URI uri = URI.create(result);

                result = uri.getHost();

            } catch (Exception e) {

                log.warn(
                        "[TENANT] Invalid tenant domain '{}'",
                        domain
                );

                return null;
            }
        }

        if (result == null || result.isBlank()) {
            return null;
        }

        /*
         * Remove port.
         */
        int colonIndex = result.indexOf(':');

        if (colonIndex > -1) {
            result = result.substring(0, colonIndex);
        }

        /*
         * Remove www.
         */
        if (result.startsWith("www.")) {
            result = result.substring(4);
        }

        /*
         * Remove trailing dot.
         */
        while (result.endsWith(".")) {
            result = result.substring(0, result.length() - 1);
        }

        return result;
    }

    /**
     * Domains that belong to your platform/infrastructure rather
     * than to a tenant.
     */
    private boolean isInfrastructureDomain(String domain) {

        if (domain == null) {
            return true;
        }

        String normalized = domain.toLowerCase();

        /*
         * Your Render backend.
         */
        if (normalized.equals("fintech01.onrender.com")) {
            return true;
        }

        /*
         * Your Vercel platform frontend.
         */
        if (normalized.equals("fintech01-aydw.vercel.app")) {
            return true;
        }

        /*
         * Local development.
         */
        if (normalized.equals("localhost") ||
                normalized.equals("127.0.0.1")) {
            return true;
        }

        return false;
    }
}
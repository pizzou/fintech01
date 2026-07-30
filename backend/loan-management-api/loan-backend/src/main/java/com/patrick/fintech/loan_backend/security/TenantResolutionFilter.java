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

    private static final String TENANT_DOMAIN_HEADER = "X-Tenant-Domain";

    private static final String ORIGIN_HEADER = "Origin";

    private final OrganizationRepository organizationRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        TenantContext.clear();

        try {

            String tenantDomain = request.getHeader(TENANT_DOMAIN_HEADER);
            String origin = request.getHeader(ORIGIN_HEADER);
            String host = request.getHeader("Host");

            log.info(
                    "[TENANT] {} {} | TenantDomain={} | Origin={} | Host={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    tenantDomain,
                    origin,
                    host
            );

            /*
             * ============================================================
             * 1. EXPLICIT X-Tenant-Domain
             * ============================================================
             *
             * This is the preferred mechanism.
             *
             * Example:
             *
             * X-Tenant-Domain: growthfinance.rw
             *
             * The frontend should send the customer's actual domain here.
             */
            if (tenantDomain != null && !tenantDomain.isBlank()) {

                String normalizedDomain = normalizeDomain(tenantDomain);

                if (normalizedDomain == null) {
                    log.warn(
                            "[TENANT] Invalid X-Tenant-Domain: {}",
                            tenantDomain
                    );

                    sendInvalidTenant(response);
                    return;
                }

                /*
                 * Platform frontend domains are NOT tenants.
                 *
                 * These are your LoanSaaS client/platform frontends.
                 */
                if (isPlatformDomain(normalizedDomain)) {

                    log.debug(
                            "[TENANT] Platform frontend '{}' - no tenant resolution required",
                            normalizedDomain
                    );

                    filterChain.doFilter(request, response);
                    return;
                }

                /*
                 * Backend infrastructure is NOT a tenant.
                 */
                if (isInfrastructureDomain(normalizedDomain)) {

                    log.debug(
                            "[TENANT] Infrastructure domain '{}' - skipping tenant resolution",
                            normalizedDomain
                    );

                    filterChain.doFilter(request, response);
                    return;
                }

                Optional<Organization> organization =
                        organizationRepository.findByDomainIgnoreCase(
                                normalizedDomain
                        );

                if (organization.isPresent()) {

                    Organization org = organization.get();

                    TenantContext.set(org);

                    log.info(
                            "[TENANT] Resolved organization '{}' (id={}) from X-Tenant-Domain='{}'",
                            org.getName(),
                            org.getId(),
                            normalizedDomain
                    );

                    filterChain.doFilter(request, response);
                    return;
                }

                /*
                 * A non-platform domain was explicitly supplied but does
                 * not exist in the organizations table.
                 */
                log.warn(
                        "[TENANT] Unknown tenant domain: {}",
                        normalizedDomain
                );

                sendUnknownTenant(response);
                return;
            }

            /*
             * ============================================================
             * 2. RESOLVE FROM ORIGIN
             * ============================================================
             *
             * Useful when the browser is accessing:
             *
             * https://growthfinance.rw
             *
             * and the frontend did not explicitly send X-Tenant-Domain.
             */
            String originDomain = extractOriginDomain(origin);

            if (originDomain != null) {

                /*
                 * Platform frontends are clients/platform applications,
                 * not tenants.
                 */
                if (isPlatformDomain(originDomain)) {

                    log.debug(
                            "[TENANT] Platform Origin '{}' - no tenant resolution required",
                            originDomain
                    );

                    filterChain.doFilter(request, response);
                    return;
                }

                /*
                 * Backend/local infrastructure is not a tenant.
                 */
                if (isInfrastructureDomain(originDomain)) {

                    log.debug(
                            "[TENANT] Infrastructure Origin '{}' - no tenant resolution required",
                            originDomain
                    );

                    filterChain.doFilter(request, response);
                    return;
                }

                /*
                 * Try to resolve the customer's domain.
                 */
                Optional<Organization> organization =
                        organizationRepository.findByDomainIgnoreCase(
                                originDomain
                        );

                if (organization.isPresent()) {

                    Organization org = organization.get();

                    TenantContext.set(org);

                    log.info(
                            "[TENANT] Resolved organization '{}' (id={}) from Origin='{}'",
                            org.getName(),
                            org.getId(),
                            originDomain
                    );

                    filterChain.doFilter(request, response);
                    return;
                }

                /*
                 * Unknown Origin should not automatically break platform
                 * endpoints. Tenant-aware services can enforce tenant
                 * requirements where necessary.
                 */
                log.debug(
                        "[TENANT] No organization found for Origin '{}'",
                        originDomain
                );
            }

            /*
             * ============================================================
             * 3. NO TENANT
             * ============================================================
             *
             * This is allowed.
             *
             * Examples:
             *
             * /api/auth/login
             * /api/public/tenant/growthfinance
             * /actuator/health
             * platform/admin endpoints
             *
             * Tenant-aware services/security should enforce tenant
             * requirements where appropriate.
             */
            log.debug(
                    "[TENANT] No tenant resolved for {} {}",
                    request.getMethod(),
                    request.getRequestURI()
            );

            filterChain.doFilter(request, response);

        } finally {

            /*
             * IMPORTANT:
             *
             * TenantContext uses ThreadLocal, therefore it MUST always
             * be cleared after the request.
             */
            TenantContext.clear();
        }
    }

    /**
     * Extract hostname from the browser Origin.
     *
     * Example:
     *
     * https://growthfinance.rw
     *
     * becomes:
     *
     * growthfinance.rw
     */
    private String extractOriginDomain(String origin) {

        if (origin == null || origin.isBlank()) {
            return null;
        }

        try {

            URI uri = URI.create(origin.trim());

            return normalizeDomain(uri.getHost());

        } catch (Exception e) {

            log.warn(
                    "[TENANT] Could not parse Origin '{}'",
                    origin
            );

            return null;
        }
    }

    /**
     * Normalize possible domain representations.
     *
     * Examples:
     *
     * https://growthfinance.rw
     * http://growthfinance.rw/
     * growthfinance.rw
     * www.growthfinance.rw
     * growthfinance.rw:443
     *
     * all become:
     *
     * growthfinance.rw
     */
    private String normalizeDomain(String domain) {

        if (domain == null || domain.isBlank()) {
            return null;
        }

        String result = domain.trim().toLowerCase();

        /*
         * Full URL.
         */
        if (result.startsWith("http://")
                || result.startsWith("https://")) {

            try {

                URI uri = URI.create(result);

                result = uri.getHost();

            } catch (Exception e) {

                log.warn(
                        "[TENANT] Invalid domain '{}'",
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
     * Your two Vercel applications are platform/client frontends.
     *
     * They are NOT organizations.
     *
     * Therefore:
     *
     * fintech01-aydw.vercel.app
     * nobleloan-fev7-one.vercel.app
     *
     * must never be looked up in organizations.domain.
     */
    private boolean isPlatformDomain(String domain) {

        if (domain == null || domain.isBlank()) {
            return false;
        }

        String normalized = domain.toLowerCase();

        return normalized.equals("fintech01-aydw.vercel.app")
                || normalized.equals("nobleloan-fev7-one.vercel.app");
    }

    /**
     * Infrastructure domains belong to LoanSaaS itself.
     *
     * They are never tenants.
     */
    private boolean isInfrastructureDomain(String domain) {

        if (domain == null || domain.isBlank()) {
            return false;
        }

        String normalized = domain.toLowerCase();

        return normalized.equals("fintech01.onrender.com")
                || normalized.equals("localhost")
                || normalized.equals("127.0.0.1");
    }

    /**
     * Invalid tenant header.
     */
    private void sendInvalidTenant(
            HttpServletResponse response
    ) throws IOException {

        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("application/json");

        response.getWriter().write(
                "{\"success\":false,\"error\":\"Invalid tenant domain.\"}"
        );
    }

    /**
     * Explicitly supplied unknown customer domain.
     */
    private void sendUnknownTenant(
            HttpServletResponse response
    ) throws IOException {

        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("application/json");

        response.getWriter().write(
                "{\"success\":false,\"error\":\"Unknown organization.\"}"
        );
    }
}


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

            String tenantKey =
                    normalize(request.getHeader(TENANT_KEY_HEADER));

            String tenantDomain =
                    normalize(request.getHeader(TENANT_DOMAIN_HEADER));

            String origin =
                    request.getHeader("Origin");

            String originDomain =
                    extractOriginDomain(origin);

            String host =
                    normalize(request.getHeader("Host"));

            log.info(
                    "[TENANT] {} {} | TenantKey={} | TenantDomain={} | Origin={} | OriginDomain={} | Host={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    tenantKey,
                    tenantDomain,
                    origin,
                    originDomain,
                    host
            );

            /*
             * ============================================================
             * 1. TENANT KEY
             * ============================================================
             */

            if (tenantKey != null) {

                Optional<Organization> organization =
                        organizationRepository
                                .findByTenantKeyIgnoreCase(tenantKey);

                if (organization.isPresent()) {

                    Organization org = organization.get();

                    TenantContext.set(org);

                    log.info(
                            "[TENANT] Resolved '{}' (id={}) using tenant key '{}'",
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
             * ============================================================
             * 2. EXPLICIT X-TENANT-DOMAIN
             * ============================================================
             */

            if (tenantDomain != null) {

                Optional<Organization> organization =
                        organizationRepository
                                .findByDomainIgnoreCase(tenantDomain);

                if (organization.isPresent()) {

                    Organization org = organization.get();

                    TenantContext.set(org);

                    log.info(
                            "[TENANT] Resolved '{}' (id={}) using X-Tenant-Domain '{}'",
                            org.getName(),
                            org.getId(),
                            tenantDomain
                    );

                    filterChain.doFilter(request, response);
                    return;
                }

                log.warn(
                        "[TENANT] Unknown X-Tenant-Domain: {}",
                        tenantDomain
                );

                sendUnknownTenant(response);
                return;
            }

            /*
             * ============================================================
             * 3. ORIGIN
             *
             * This is important for your two Vercel tenant websites:
             *
             * fintech01-aydw.vercel.app
             * nobleloan-fev7-one.vercel.app
             *
             * We DO NOT automatically reject *.vercel.app.
             * Instead we check the Organization table.
             * ============================================================
             */

            if (originDomain != null) {

                Optional<Organization> organization =
                        organizationRepository
                                .findByDomainIgnoreCase(originDomain);

                if (organization.isPresent()) {

                    Organization org = organization.get();

                    TenantContext.set(org);

                    log.info(
                            "[TENANT] Resolved '{}' (id={}) from Origin '{}'",
                            org.getName(),
                            org.getId(),
                            originDomain
                    );

                    filterChain.doFilter(request, response);
                    return;
                }

                /*
                 * If the origin is one of the platform/admin applications,
                 * continue without a tenant.
                 */
                if (isPlatformDomain(originDomain)) {

                    log.debug(
                            "[TENANT] Platform origin '{}'; no tenant required",
                            originDomain
                    );

                    filterChain.doFilter(request, response);
                    return;
                }

                log.warn(
                        "[TENANT] Unknown tenant origin: {}",
                        originDomain
                );

                sendUnknownTenant(response);
                return;
            }

            /*
             * ============================================================
             * 4. HOST FALLBACK
             *
             * Usually this will be fintech01.onrender.com because the
             * frontend and backend are on different domains.
             *
             * We therefore don't use Host as the primary tenant source.
             * ============================================================
             */

            if (host != null && !isPlatformDomain(host)) {

                Optional<Organization> organization =
                        organizationRepository
                                .findByDomainIgnoreCase(host);

                if (organization.isPresent()) {

                    Organization org = organization.get();

                    TenantContext.set(org);

                    log.info(
                            "[TENANT] Resolved '{}' (id={}) from Host '{}'",
                            org.getName(),
                            org.getId(),
                            host
                    );

                    filterChain.doFilter(request, response);
                    return;
                }
            }

            /*
             * ============================================================
             * 5. NO TENANT
             *
             * Allow platform/public endpoints that don't require a tenant.
             * Authentication/authorization can enforce tenant requirements
             * later where appropriate.
             * ============================================================
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

    /**
     * Extract hostname from Origin.
     */
    private String extractOriginDomain(String origin) {

        if (origin == null || origin.isBlank()) {
            return null;
        }

        try {

            URI uri = URI.create(origin.trim());

            return normalize(uri.getHost());

        } catch (Exception e) {

            log.warn(
                    "[TENANT] Could not parse Origin '{}'",
                    origin
            );

            return null;
        }
    }

    /**
     * Normalize domains and headers.
     */
    private String normalize(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        String result =
                value.trim().toLowerCase();

        if (result.startsWith("http://")
                || result.startsWith("https://")) {

            try {

                URI uri =
                        URI.create(result);

                result =
                        uri.getHost();

            } catch (Exception e) {

                return null;
            }
        }

        if (result == null || result.isBlank()) {
            return null;
        }

        /*
         * Remove port.
         */
        int colon =
                result.indexOf(':');

        if (colon > -1) {

            result =
                    result.substring(0, colon);
        }

        /*
         * Remove www.
         */
        if (result.startsWith("www.")) {

            result =
                    result.substring(4);
        }

        /*
         * Remove trailing dots.
         */
        while (result.endsWith(".")) {

            result =
                    result.substring(
                            0,
                            result.length() - 1
                    );
        }

        return result;
    }

    /**
     * Domains belonging to the LoanSaaS infrastructure itself.
     *
     * IMPORTANT:
     *
     * Tenant Vercel websites are NOT listed here.
     */
    private boolean isPlatformDomain(String domain) {

        if (domain == null) {
            return true;
        }

        String normalized =
                normalize(domain);

        if (normalized == null) {
            return true;
        }

        /*
         * Backend
         */
        if (normalized.equals("fintech01.onrender.com")) {
            return true;
        }

        /*
         * Local development
         */
        if (normalized.equals("localhost")
                || normalized.equals("127.0.0.1")) {
            return true;
        }

        /*
         * IMPORTANT:
         *
         * Do NOT put:
         *
         * fintech01-aydw.vercel.app
         * nobleloan-fev7-one.vercel.app
         *
         * here.
         *
         * They are tenant domains.
         */

        return false;
    }

    private void sendUnknownTenant(
            HttpServletResponse response
    ) throws IOException {

        response.setStatus(
                HttpServletResponse.SC_BAD_REQUEST
        );

        response.setContentType(
                "application/json"
        );

        response.getWriter().write(
                "{\"success\":false,\"error\":\"Invalid tenant. Please access your organization's official website.\"}"
        );
    }
}

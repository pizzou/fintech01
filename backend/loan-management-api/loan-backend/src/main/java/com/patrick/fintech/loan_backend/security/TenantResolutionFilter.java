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
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class TenantResolutionFilter extends OncePerRequestFilter {

    private final OrganizationRepository organizationRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            /*
             * IMPORTANT:
             *
             * Browser:
             *   https://growthfinance.rw
             *
             * calls:
             *   https://fintech01.onrender.com
             *
             * Therefore request.getServerName() is:
             *   fintech01.onrender.com
             *
             * NOT:
             *   growthfinance.rw
             *
             * So we first check X-Tenant-Domain, which the frontend sends.
             */

            String domain = request.getHeader("X-Tenant-Domain");

            /*
             * If X-Tenant-Domain wasn't supplied, fall back to Origin.
             *
             * Example:
             * Origin: https://growthfinance.rw
             */
            if (domain == null || domain.isBlank()) {
                domain = extractDomainFromOrigin(request.getHeader("Origin"));
            }

            /*
             * Last fallback: the API hostname itself.
             *
             * This is useful for direct API requests/local testing.
             */
            if (domain == null || domain.isBlank()) {
                domain = request.getServerName();
            }

            domain = normalizeDomain(domain);

            log.info(
                "[TENANT] {} {} | X-Tenant-Domain={} | Origin={} | ResolvedDomain={}",
                request.getMethod(),
                request.getRequestURI(),
                request.getHeader("X-Tenant-Domain"),
                request.getHeader("Origin"),
                domain
            );

            /*
             * Clear any previous tenant from the current thread.
             */
            TenantContext.clear();

            /*
             * Don't try to resolve obvious infrastructure/local hosts.
             */
            if (domain != null && !domain.isBlank()) {

                Optional<Organization> organization =
                        organizationRepository.findByDomainIgnoreCase(domain);

                if (organization.isPresent()) {

                    Organization org = organization.get();

                    TenantContext.set(org);

                    log.info(
                        "[TENANT] Resolved organization '{}' (id={}) from domain '{}'",
                        org.getName(),
                        org.getId(),
                        domain
                    );

                } else {

                    log.warn(
                        "[TENANT] No organization found for domain '{}'",
                        domain
                    );
                }
            }

            filterChain.doFilter(request, response);

        } finally {

            /*
             * VERY IMPORTANT:
             *
             * TenantContext normally uses ThreadLocal.
             * We must clear it after every request so one tenant
             * cannot leak into another request.
             */
            TenantContext.clear();
        }
    }

    /**
     * Extract hostname from:
     *
     * https://growthfinance.rw
     * https://www.growthfinance.rw/
     *
     * Returns:
     * growthfinance.rw
     */
    private String extractDomainFromOrigin(String origin) {

        if (origin == null || origin.isBlank()) {
            return null;
        }

        try {

            java.net.URI uri = java.net.URI.create(origin);

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
     * Converts:
     *
     * https://growthfinance.rw
     * http://growthfinance.rw/
     * www.growthfinance.rw
     * growthfinance.rw:443
     *
     * into:
     *
     * growthfinance.rw
     */
    private String normalizeDomain(String domain) {

        if (domain == null || domain.isBlank()) {
            return null;
        }

        String result = domain.trim().toLowerCase();

        /*
         * If somebody accidentally sends a full URL,
         * extract only the hostname.
         */
        if (result.startsWith("http://") || result.startsWith("https://")) {

            try {

                result = java.net.URI.create(result).getHost();

            } catch (Exception e) {

                log.warn(
                    "[TENANT] Invalid tenant domain '{}'",
                    domain
                );

                return null;
            }
        }

        /*
         * Remove port.
         */
        if (result.contains(":")) {
            result = result.substring(0, result.indexOf(":"));
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
        if (result.endsWith(".")) {
            result = result.substring(0, result.length() - 1);
        }

        return result;
    }
}
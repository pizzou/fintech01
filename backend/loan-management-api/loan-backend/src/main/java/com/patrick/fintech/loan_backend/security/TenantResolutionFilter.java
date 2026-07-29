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

@Slf4j
@Component
@RequiredArgsConstructor
public class TenantResolutionFilter extends OncePerRequestFilter {

    private final OrganizationRepository orgRepo;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            String domain = null;

            /*
             * 1. Explicit tenant domain supplied by the frontend.
             *
             * Example:
             *
             * X-Tenant-Domain: growthfinance.rw
             */
            String tenantHeader = request.getHeader("X-Tenant-Domain");

            if (tenantHeader != null && !tenantHeader.isBlank()) {
                domain = normalizeHost(tenantHeader);
            }

            /*
             * 2. If there is no explicit tenant domain, try the Host.
             *
             * This works when the borrower/customer accesses:
             *
             * https://growthfinance.rw
             *
             * and that request is proxied to the backend.
             *
             * It will NOT identify a tenant when the request is sent
             * directly to:
             *
             * https://fintech01.onrender.com
             */
            if (domain == null) {
                domain = normalizeHost(request.getHeader("Host"));
            }

            /*
             * Make the value final before using it in lambdas.
             */
            final String resolvedDomain = domain;

            if (resolvedDomain != null) {

                orgRepo
                    .findByDomainIgnoreCaseAndDomainVerifiedTrue(resolvedDomain)
                    .ifPresentOrElse(
                        organization -> {

                            TenantContext.set(organization);

                            log.debug(
                                "Resolved tenant '{}' from domain '{}'",
                                organization.getName(),
                                resolvedDomain
                            );
                        },

                        () -> {

                            log.debug(
                                "No verified organization found for domain '{}'",
                                resolvedDomain
                            );
                        }
                    );
            }

            /*
             * Always continue the request.
             *
             * The filter itself does NOT reject requests when no tenant
             * can be resolved.
             */
            filterChain.doFilter(request, response);

        } finally {

            /*
             * VERY IMPORTANT.
             *
             * TenantContext is usually backed by ThreadLocal.
             * Clear it after every request because application-server
             * threads are reused.
             */
            TenantContext.clear();
        }
    }

    /**
     * Normalizes a domain.
     *
     * Examples:
     *
     * www.growthfinance.rw:443 -> growthfinance.rw
     * growthfinance.rw        -> growthfinance.rw
     * WWW.GROWTHFINANCE.RW     -> growthfinance.rw
     */
    private String normalizeHost(String host) {

        if (host == null || host.isBlank()) {
            return null;
        }

        String h = host.trim().toLowerCase();

        /*
         * Remove protocol if somebody accidentally sends it.
         */
        if (h.startsWith("https://")) {
            h = h.substring(8);
        } else if (h.startsWith("http://")) {
            h = h.substring(7);
        }

        /*
         * Remove path.
         */
        int slash = h.indexOf('/');

        if (slash >= 0) {
            h = h.substring(0, slash);
        }

        /*
         * Remove port.
         */
        int colon = h.indexOf(':');

        if (colon >= 0) {
            h = h.substring(0, colon);
        }

        /*
         * Remove www.
         */
        if (h.startsWith("www.")) {
            h = h.substring(4);
        }

        return h.isBlank() ? null : h;
    }
}
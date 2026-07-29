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

/**
 * Reads the Host header on every incoming request (e.g. "www.growthfinance.rw"),
 * strips "www." and any port, and looks up the matching Organization by its
 * `domain` column. If found, stashes it in TenantContext for the duration of
 * the request so downstream code (PublicController etc.) can resolve the
 * tenant without the frontend having to pass a tenantSlug.
 *
 * Runs before Spring Security's auth filters so it applies to both the
 * anonymous /api/public/** endpoints and, later, any staff-side domain
 * checks you want to add. It intentionally never rejects a request on its
 * own — an unmatched domain just means TenantContext.get() returns null,
 * and callers fall back to whatever they were doing before (e.g. request-body
 * tenantSlug, useful for local dev / api.loansaas.com / admin.loansaas.com).
 */
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
        String resolvedDomain = normalizeHost(
                request.getHeader("X-Tenant-Domain")
        );

        // If X-Tenant-Domain is not provided, use the Host header.
        if (resolvedDomain == null) {
            resolvedDomain = normalizeHost(
                    request.getHeader("Host")
            );
        }

        // Make it final so it can safely be used inside the lambda.
        final String domain = resolvedDomain;

        if (domain != null) {

            orgRepo.findByDomainIgnoreCaseAndDomainVerifiedTrue(domain)
                    .ifPresentOrElse(
                            org -> {
                                TenantContext.set(org);

                                log.debug(
                                        "Resolved tenant '{}' from host '{}'",
                                        org.getName(),
                                        domain
                                );
                            },
                            () -> {
                                log.debug(
                                        "No VERIFIED organization for host '{}' " +
                                        "(unclaimed, or claimed but not yet DNS-verified)",
                                        domain
                                );
                            }
                    );
        }

        filterChain.doFilter(request, response);

    } finally {

        // Very important:
        // remove tenant information after every request because
        // Spring may reuse the same thread for another request.
        TenantContext.clear();
    }
}
    /** "www.growthfinance.rw:443" -> "growthfinance.rw". Returns null for blank/localhost-ish hosts. */
    private String normalizeHost(String host) {
        if (host == null || host.isBlank()) return null;
        String h = host.trim().toLowerCase();
        int colon = h.indexOf(':');
        if (colon >= 0) h = h.substring(0, colon);
        if (h.startsWith("www.")) h = h.substring(4);
        return h.isBlank() ? null : h;
    }
}
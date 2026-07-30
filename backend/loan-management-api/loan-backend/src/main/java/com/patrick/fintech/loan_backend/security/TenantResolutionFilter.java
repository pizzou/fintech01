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
                    "[TENANT] {} {} | TenantDomain={} | Origin={} | Host={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    tenantDomain,
                    request.getHeader("Origin"),
                    request.getHeader("Host")
            );

           
            if (tenantDomain == null || tenantDomain.isBlank()) {

                filterChain.doFilter(request, response);
                return;
            }

          
            if (isInfrastructureDomain(tenantDomain)) {

                filterChain.doFilter(request, response);
                return;
            }

           
            Optional<Organization> organizationOptional =
                    organizationRepository.findByDomainIgnoreCase(tenantDomain);

            if (organizationOptional.isEmpty()) {

                log.warn(
                        "[TENANT] Unknown tenant domain: {}",
                        tenantDomain
                );

                response.setStatus(
                        HttpServletResponse.SC_BAD_REQUEST
                );

                response.setContentType("application/json");

                response.getWriter().write(
                        "{\"success\":false,\"error\":\"Unknown tenant.\"}"
                );

                return;
            }

            Organization organization =
                    organizationOptional.get();

            
            if (organization.getStatus() != Organization.OrgStatus.ACTIVE
                    && organization.getStatus() != Organization.OrgStatus.PENDING_SETUP) {

                log.warn(
                        "[TENANT] Organization {} is not active",
                        organization.getName()
                );

                response.setStatus(
                        HttpServletResponse.SC_FORBIDDEN
                );

                response.setContentType("application/json");

                response.getWriter().write(
                        "{\"success\":false,\"error\":\"Organization is not active.\"}"
                );

                return;
            }

            TenantContext.set(organization);

            log.info(
                    "[TENANT] Resolved '{}' id={} domain={}",
                    organization.getName(),
                    organization.getId(),
                    tenantDomain
            );

            filterChain.doFilter(request, response);

        } finally {

            TenantContext.clear();
        }
    }

    private String resolveTenantDomain(
            HttpServletRequest request
    ) {

       
        String domain =
                request.getHeader(TENANT_HEADER);

        if (domain != null && !domain.isBlank()) {
            return normalizeDomain(domain);
        }

       
        String origin =
                request.getHeader("Origin");

        if (origin != null && !origin.isBlank()) {

            String originDomain =
                    extractDomainFromOrigin(origin);

            if (originDomain != null
                    && !originDomain.isBlank()) {

                return normalizeDomain(originDomain);
            }
        }

        return null;
    }

    private String extractDomainFromOrigin(
            String origin
    ) {

        try {

            URI uri =
                    URI.create(origin.trim());

            return uri.getHost();

        } catch (Exception e) {

            log.warn(
                    "[TENANT] Invalid Origin: {}",
                    origin
            );

            return null;
        }
    }

    private String normalizeDomain(
            String domain
    ) {

        if (domain == null || domain.isBlank()) {
            return null;
        }

        String result =
                domain.trim().toLowerCase();

        if (result.startsWith("http://")
                || result.startsWith("https://")) {

            try {

                URI uri =
                        URI.create(result);

                result = uri.getHost();

            } catch (Exception e) {

                return null;
            }
        }

        if (result == null || result.isBlank()) {
            return null;
        }

        
        int colonIndex =
                result.indexOf(':');

        if (colonIndex > -1) {
            result =
                    result.substring(0, colonIndex);
        }

    
        if (result.startsWith("www.")) {
            result =
                    result.substring(4);
        }

        
        while (result.endsWith(".")) {
            result =
                    result.substring(
                            0,
                            result.length() - 1
                    );
        }

        return result;
    }

    private boolean isInfrastructureDomain(
            String domain
    ) {

        if (domain == null) {
            return true;
        }

        String normalized =
                domain.toLowerCase();

        if (normalized.equals(
                "fintech01.onrender.com"
        )) {
            return true;
        }

        
        if (normalized.equals("localhost")
                || normalized.equals("127.0.0.1")) {
            return true;
        }

     

        return false;
    }
}
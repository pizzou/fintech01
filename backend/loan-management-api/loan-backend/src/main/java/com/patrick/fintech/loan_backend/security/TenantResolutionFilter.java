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

    private final OrganizationRepository organizationRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        TenantContext.clear();

        try {

            String tenantHeader =
                    request.getHeader(TENANT_DOMAIN_HEADER);

            String origin =
                    request.getHeader("Origin");

            String host =
                    request.getHeader("Host");

            log.info(
                    "[TENANT] {} {} | TenantDomain={} | Origin={} | Host={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    tenantHeader,
                    origin,
                    host
            );

           
            if (tenantHeader != null && !tenantHeader.isBlank()) {

                String normalizedDomain =
                        normalizeDomain(tenantHeader);

                if (normalizedDomain != null) {

                    Optional<Organization> organization =
                            organizationRepository
                                    .findByDomainIgnoreCase(
                                            normalizedDomain
                                    );

                    if (organization.isPresent()) {

                        Organization org =
                                organization.get();

                        TenantContext.set(org);

                        log.info(
                                "[TENANT] Resolved '{}' (id={}) using X-Tenant-Domain={}",
                                org.getName(),
                                org.getId(),
                                normalizedDomain
                        );

                        filterChain.doFilter(
                                request,
                                response
                        );

                        return;
                    }

                    log.warn(
                            "[TENANT] Unknown tenant domain: {}",
                            normalizedDomain
                    );

                    sendUnknownTenant(response);
                    return;
                }
            }

            
            String originDomain =
                    extractOriginDomain(origin);

            if (originDomain != null) {

               
                Optional<Organization> organization =
                        organizationRepository
                                .findByDomainIgnoreCase(
                                        originDomain
                                );

                if (organization.isPresent()) {

                    Organization org =
                            organization.get();

                    TenantContext.set(org);

                    log.info(
                            "[TENANT] Resolved '{}' (id={}) from Origin={}",
                            org.getName(),
                            org.getId(),
                            originDomain
                    );

                    filterChain.doFilter(
                            request,
                            response
                    );

                    return;
                }

               
                log.warn(
                        "[TENANT] Unknown tenant domain from Origin: {}",
                        originDomain
                );

                sendUnknownTenant(response);
                return;
            }

           
            log.debug(
                    "[TENANT] No tenant domain supplied for {} {}",
                    request.getMethod(),
                    request.getRequestURI()
            );

            filterChain.doFilter(
                    request,
                    response
            );

        } finally {

            TenantContext.clear();
        }
    }

    
    private String extractOriginDomain(
            String origin
    ) {

        if (origin == null || origin.isBlank()) {
            return null;
        }

        try {

            URI uri =
                    URI.create(origin.trim());

            return normalizeDomain(
                    uri.getHost()
            );

        } catch (Exception e) {

            log.warn(
                    "[TENANT] Could not parse Origin={}",
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
                domain.trim()
                        .toLowerCase();

       
        if (
                result.startsWith("http://")
                        || result.startsWith("https://")
        ) {

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

        
        int colon =
                result.indexOf(':');

        if (colon > -1) {

            result =
                    result.substring(
                            0,
                            colon
                    );
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

    /**
     * Return a consistent tenant error.
     */
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
                """
                {
                  "success": false,
                  "error": "Invalid tenant. Please access your organization's official website."
                }
                """
        );
    }
}

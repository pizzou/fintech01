package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.repository.OrganizationRepository;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import com.patrick.fintech.loan_backend.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Cross-tenant management for the platform owner only — this is the backend
 * for what should be its own separate app (admin.loansaas.com in the
 * architecture doc), never surfaced to org staff or customers.
 *
 * Every endpoint here is restricted to SUPER_ADMIN, a role that isn't
 * assignable from any per-org endpoint (see OrganizationController, which
 * only ever touches the caller's own org). A SUPER_ADMIN user has
 * organization_id = NULL, so JwtAuthFilter's normal per-org scoping never
 * applies to them — deliberately: this controller is the one place in the
 * codebase allowed to see every tenant at once.
 *
 * Deploy this behind its own subdomain (admin.loansaas.com) at the reverse
 * proxy / load balancer level too — don't rely on @PreAuthorize alone as
 * your only defense for something this sensitive.
 */
@RestController
@RequestMapping("/api/super-admin/organizations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminOrganizationController {

    private final OrganizationRepository orgRepo;
    private final CurrentUserUtil currentUserUtil;
    private final AuditService auditService;

    /** Every organization on the platform — name, domain, status, plan. Never exposed to org-scoped users. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Organization>>> listAll() {
        return ResponseEntity.ok(ApiResponse.ok(orgRepo.findAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Organization>> getOne(@PathVariable Long id) {
        Organization org = orgRepo.findById(id)
            .orElseThrow(() -> new RuntimeException("Organization not found"));
        return ResponseEntity.ok(ApiResponse.ok(org));
    }

    /**
     * Onboards a new tenant. Only the fields needed to stand the org up are
     * accepted here — branding/content is still self-served by that org's
     * own ADMIN via PUT /api/organizations/me once they have staff logged in.
     * Body: { name, domain, country, defaultCurrency, subscriptionTier }
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Organization>> create(@RequestBody Map<String, Object> body) {
        String domain = str(body.get("domain"));
        if (domain != null && orgRepo.findByDomainIgnoreCase(domain).isPresent()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Domain '" + domain + "' is already assigned to another organization."));
        }

        Organization org = Organization.builder()
            .name(str(body.get("name")))
            .domain(domain)
            .domainVerified(domain != null)   // trusted manual assignment, same as setDomain() above
            .country(str(body.get("country")))
            .defaultCurrency(str(body.get("defaultCurrency")))
            .subscriptionTier(body.get("subscriptionTier") != null
                ? Organization.SubscriptionTier.valueOf(str(body.get("subscriptionTier")))
                : Organization.SubscriptionTier.TRIAL)
            .status(Organization.OrgStatus.PENDING_SETUP)
            .trialEndsAt(LocalDateTime.now().plusDays(14))
            .build();

        org = orgRepo.save(org);
        auditService.log(org, currentUserUtil.getCurrentUser(), "ORGANIZATION_CREATED", "ORGANIZATION",
            org.getId().toString(), "Platform admin onboarded new organization: " + org.getName());
        return ResponseEntity.ok(ApiResponse.ok("Organization created", org));
    }

    /**
     * Manually assigns a domain — bypasses the DNS ownership proof that the
     * self-service POST /api/organizations/me/domain flow requires, so use
     * this only when you've confirmed ownership some other way yourself
     * (support ticket, direct contact with the client, etc.). This is what
     * the mapping TenantResolutionFilter reads on every request, so getting
     * it wrong here means customers land on the wrong tenant.
     */
    @PutMapping("/{id}/domain")
    public ResponseEntity<ApiResponse<Organization>> setDomain(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String domain = str(body.get("domain"));
        if (domain == null || domain.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("domain is required"));
        }
        Organization org = orgRepo.findById(id).orElseThrow(() -> new RuntimeException("Organization not found"));

        orgRepo.findByDomainIgnoreCase(domain)
            .filter(existing -> !existing.getId().equals(id))
            .ifPresent(existing -> { throw new IllegalStateException("Domain '" + domain + "' is already assigned to " + existing.getName()); });

        String previous = org.getDomain();
        org.setDomain(domain.toLowerCase());
        org.setDomainVerified(true);        // trusted manual assignment — no DNS proof required
        org.setDomainVerificationToken(null);
        org = orgRepo.save(org);
        auditService.log(org, currentUserUtil.getCurrentUser(), "ORGANIZATION_DOMAIN_CHANGED", "ORGANIZATION",
            org.getId().toString(), "Domain changed from '" + previous + "' to '" + domain + "' (manual super-admin assignment)");
        return ResponseEntity.ok(ApiResponse.ok("Domain updated", org));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<Organization>> setStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Organization org = orgRepo.findById(id).orElseThrow(() -> new RuntimeException("Organization not found"));
        Organization.OrgStatus newStatus = Organization.OrgStatus.valueOf(str(body.get("status")));
        Organization.OrgStatus previous = org.getStatus();
        org.setStatus(newStatus);
        org = orgRepo.save(org);
        auditService.log(org, currentUserUtil.getCurrentUser(), "ORGANIZATION_STATUS_CHANGED", "ORGANIZATION",
            org.getId().toString(), "Status changed from " + previous + " to " + newStatus);
        return ResponseEntity.ok(ApiResponse.ok("Status updated", org));
    }

    private String str(Object o) { return o == null ? null : o.toString().trim(); }
}
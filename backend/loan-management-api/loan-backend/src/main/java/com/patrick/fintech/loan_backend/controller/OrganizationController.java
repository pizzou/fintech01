package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.*;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.security.SecureRandom;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationRepository orgRepo;
    private final UserRepository         userRepo;
    private final CurrentUserUtil        currentUserUtil;
    private final com.patrick.fintech.loan_backend.service.AuditService auditService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Where a client should CNAME their apex/www to. Set via PLATFORM_CNAME_TARGET in production. */
    @Value("${app.platform.cname-target:sites.loansaas.com}")
    private String platformCnameTarget;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String,Object>>> getMyOrg() {
        Organization org = orgRepo.findById(currentUserUtil.getCurrentUser().getOrganization().getId())
            .orElseThrow(() -> new RuntimeException("Organization not found"));

        Map<String,Object> m = new java.util.LinkedHashMap<>();
        m.put("id", org.getId()); m.put("name", org.getName());
        // Self-service domain claim/verify — see /me/domain endpoints below.
        // "domain" only reflects a VERIFIED domain (what's actually live);
        // an unverified claim in progress shows up in domainPending instead,
        // so the dashboard can keep showing setup instructions until proven.
        m.put("domain", org.isDomainVerified() ? org.getDomain() : null);
        if (org.getDomain() != null && !org.isDomainVerified()) {
            m.put("domainPending", domainInstructions(org));
        }
        m.put("logoUrl", org.getLogoUrl()); m.put("primaryColor", org.getPrimaryColor());
        m.put("accentColor", org.getAccentColor()); m.put("website", org.getWebsite());
        m.put("contactEmail", org.getContactEmail()); m.put("contactPhone", org.getContactPhone());
        m.put("address", org.getAddress()); m.put("tagline", org.getTagline());
        m.put("mission", org.getMission()); m.put("vision", org.getVision());
        m.put("foundedYear", org.getFoundedYear()); m.put("mapUrl", org.getMapUrl());
        m.put("facebookUrl", org.getFacebookUrl()); m.put("instagramUrl", org.getInstagramUrl());
        m.put("linkedinUrl", org.getLinkedinUrl()); m.put("twitterUrl", org.getTwitterUrl());
        m.put("whatsappUrl", org.getWhatsappUrl());

        m.put("hero", Map.of(
            "headline", org.getHeroHeadline() != null ? org.getHeroHeadline() : "Your Trusted Financial Partner",
            "subtext",  org.getHeroSubtext()  != null ? org.getHeroSubtext()  : ""
        ));
        m.put("stats",        parseListOrEmpty(org.getStatsJson()));
        m.put("services",     parseListOrEmpty(org.getServicesJson()));
        m.put("testimonials", parseListOrEmpty(org.getTestimonialsJson()));
        m.put("team",         parseListOrEmpty(org.getTeamJson()));

        return ResponseEntity.ok(ApiResponse.ok(m));
    }

    private List<Map<String,Object>> parseListOrEmpty(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<Map<String,Object>> parsed = objectMapper.readValue(json,
                new com.fasterxml.jackson.core.type.TypeReference<List<Map<String,Object>>>() {});
            return parsed != null ? parsed : List.of();
        } catch (Exception e) { return List.of(); }
    }

    /**
     * Lets an org admin update their own organization's public-facing website
     * content (branding, contact info, mission/vision, social links) as well
     * as core org settings. Restricted to ADMIN — this controls what every
     * visitor to the org's public site sees.
     */
    @PutMapping("/me")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Organization>> updateMyOrg(@RequestBody Map<String,Object> body) {
        Organization org = orgRepo.findById(currentUserUtil.getCurrentOrganizationId())
            .orElseThrow(() -> new RuntimeException("Organization not found"));

        // Core org settings
        setIfPresent(body, "name",            org::setName);
        setIfPresent(body, "contactEmail",    org::setContactEmail);
        setIfPresent(body, "contactPhone",    org::setContactPhone);
        setIfPresent(body, "address",         org::setAddress);
        setIfPresent(body, "defaultCurrency", org::setDefaultCurrency);
        setIfPresent(body, "timezone",        org::setTimezone);
        setIfPresent(body, "website",         org::setWebsite);
        setIfPresent(body, "logoUrl",         org::setLogoUrl);

        // Branding
        setIfPresent(body, "primaryColor", org::setPrimaryColor);
        setIfPresent(body, "accentColor",  org::setAccentColor);

        // Public website content
        setIfPresent(body, "tagline",       org::setTagline);
        setIfPresent(body, "mission",       org::setMission);
        setIfPresent(body, "vision",        org::setVision);
        setIfPresent(body, "mapUrl",        org::setMapUrl);
        setIfPresent(body, "facebookUrl",   org::setFacebookUrl);
        setIfPresent(body, "instagramUrl",  org::setInstagramUrl);
        setIfPresent(body, "linkedinUrl",   org::setLinkedinUrl);
        setIfPresent(body, "twitterUrl",    org::setTwitterUrl);
        setIfPresent(body, "whatsappUrl",   org::setWhatsappUrl);
        if (body.containsKey("foundedYear") && body.get("foundedYear") != null) {
            try { org.setFoundedYear(Integer.valueOf(body.get("foundedYear").toString())); }
            catch (NumberFormatException ignored) {}
        }

        // Home page hero
        if (body.containsKey("hero") && body.get("hero") instanceof Map<?,?> hero) {
            if (hero.get("headline") != null) org.setHeroHeadline(hero.get("headline").toString());
            if (hero.get("subtext")  != null) org.setHeroSubtext(hero.get("subtext").toString());
        }
        // Repeatable content lists — stored as JSON, editable in full from the dashboard
        setJsonIfPresent(body, "stats",        org::setStatsJson);
        setJsonIfPresent(body, "services",     org::setServicesJson);
        setJsonIfPresent(body, "testimonials", org::setTestimonialsJson);
        setJsonIfPresent(body, "team",         org::setTeamJson);

        org = orgRepo.save(org);
        auditService.log(org, currentUserUtil.getCurrentUser(), "ORGANIZATION_UPDATED", "ORGANIZATION",
            org.getId().toString(), "Website/organization settings updated");
        return ResponseEntity.ok(ApiResponse.ok("Updated", org));
    }

    private void setIfPresent(Map<String,Object> body, String key, java.util.function.Consumer<String> setter) {
        if (body.containsKey(key) && body.get(key) != null) setter.accept(body.get(key).toString());
    }

    private void setJsonIfPresent(Map<String,Object> body, String key, java.util.function.Consumer<String> setter) {
        if (!body.containsKey(key) || body.get(key) == null) return;
        try { setter.accept(objectMapper.writeValueAsString(body.get(key))); }
        catch (Exception e) { throw new RuntimeException("Invalid " + key + " content: " + e.getMessage()); }
    }

    @GetMapping("/me/users")
    public ResponseEntity<ApiResponse<List<User>>> getUsers() {
        Organization org = currentUserUtil.getCurrentUser().getOrganization();
        return ResponseEntity.ok(ApiResponse.ok(userRepo.findByOrganization(org)));
    }

    /**
     * Self-service domain claim, step 1 of 2. An org ADMIN submits the
     * domain they want their public site served on (growthfinance.rw) —
     * this does NOT make it live yet. It generates a verification token and
     * returns DNS records the client must publish to prove they actually
     * control that domain, same pattern as Vercel/Netlify custom domains.
     * Call POST /me/domain/verify once those records are live to activate it.
     */
    @PostMapping("/me/domain")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<ApiResponse<Map<String, Object>>> claimDomain(
        @RequestBody Map<String, Object> body) {

    String domain = normalizeDomain(str(body.get("domain")));

    if (domain == null || !domain.matches(
            "^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)+$")) {

        return ResponseEntity.badRequest().body(
                ApiResponse.error(
                        "Enter a plain domain like growthfinance.rw — " +
                        "no https://, no www., no trailing slash or path."
                )
        );
    }

    Long organizationId = currentUserUtil.getCurrentOrganizationId();

    Organization org = orgRepo.findById(organizationId)
            .orElseThrow(() ->
                    new RuntimeException("Organization not found")
            );

    /*
     * Check whether another organization already owns this domain.
     *
     * Do NOT reassign org after this point because org is captured
     * by the lambda below.
     */
    boolean alreadyClaimed = orgRepo.findByDomainIgnoreCase(domain)
            .map(existing -> !existing.getId().equals(organizationId))
            .orElse(false);

    if (alreadyClaimed) {
        return ResponseEntity.badRequest().body(
                ApiResponse.error(
                        "That domain is already claimed by another organization " +
                        "on this platform. If this is your domain and you believe " +
                        "this is a mistake, contact support."
                )
        );
    }

    /*
     * Generate DNS verification token.
     */
    String token = "loansaas-verify=" + randomToken();

    org.setDomain(domain);
    org.setDomainVerified(false);
    org.setDomainVerificationToken(token);

    /*
     * Do not assign the result back to org.
     *
     * save() returns the same managed entity in the normal JPA case,
     * and we don't need the returned value here.
     */
    orgRepo.save(org);

    auditService.log(
            org,
            currentUserUtil.getCurrentUser(),
            "ORGANIZATION_DOMAIN_CLAIMED",
            "ORGANIZATION",
            org.getId().toString(),
            "Claimed domain '" + domain + "' — pending DNS verification"
    );

    return ResponseEntity.ok(
            ApiResponse.ok(
                    "Domain claimed — follow the DNS instructions to activate it",
                    domainInstructions(org)
            )
    );
}

    /**
     * Self-service domain claim, step 2 of 2. Looks up the TXT record the
     * client was asked to publish under _loansaas-verify.<their domain> and,
     * if it matches the token from claimDomain(), flips domainVerified to
     * true — from that moment TenantResolutionFilter and CORS start
     * trusting the domain and it goes live. DNS propagation can take a
     * while, so this is safe to retry; nothing bad happens on a "not found yet".
     */
    @PostMapping("/me/domain/verify")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String,Object>>> verifyDomain() {
        Organization org = orgRepo.findById(currentUserUtil.getCurrentOrganizationId())
            .orElseThrow(() -> new RuntimeException("Organization not found"));

        if (org.getDomain() == null || org.getDomainVerificationToken() == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("No domain claim in progress — call POST /me/domain first."));
        }
        if (org.isDomainVerified()) {
            return ResponseEntity.ok(ApiResponse.ok("Already verified", Map.of("domain", org.getDomain(), "domainVerified", true)));
        }

        boolean found = txtRecordContains("_loansaas-verify." + org.getDomain(), org.getDomainVerificationToken());
        if (!found) {
            return ResponseEntity.status(409).body(ApiResponse.error(
                "Verification record not found yet. DNS changes can take up to a few hours to propagate — add the TXT record shown and try again shortly."));
        }

        org.setDomainVerified(true);
        org.setDomainVerificationToken(null);
        org = orgRepo.save(org);
        auditService.log(org, currentUserUtil.getCurrentUser(), "ORGANIZATION_DOMAIN_VERIFIED", "ORGANIZATION",
            org.getId().toString(), "Domain '" + org.getDomain() + "' verified and is now live");

        return ResponseEntity.ok(ApiResponse.ok("Domain verified — your site is now live on " + org.getDomain(),
            Map.of("domain", org.getDomain(), "domainVerified", true)));
    }

    /** DNS records + status shown to the client while a domain claim is unverified. */
    private Map<String,Object> domainInstructions(Organization org) {
        Map<String,Object> m = new java.util.LinkedHashMap<>();
        m.put("domain", org.getDomain());
        m.put("domainVerified", org.isDomainVerified());
        m.put("dnsRecords", List.of(
            Map.of("type", "TXT", "name", "_loansaas-verify." + org.getDomain(), "value", org.getDomainVerificationToken(),
                "purpose", "Proves you control this domain. Remove after verifying, if you like."),
            Map.of("type", "CNAME", "name", "www." + org.getDomain(), "value", platformCnameTarget,
                "purpose", "Points your website traffic at this platform.")
        ));
        return m;
    }

    /** DNS TXT lookup via JNDI — no extra dependency needed. Fails closed (returns false) on any lookup error. */
    private boolean txtRecordContains(String recordName, String expectedValue) {
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            env.put("com.sun.jndi.dns.timeout.initial", "3000");
            env.put("com.sun.jndi.dns.timeout.retries", "2");
            DirContext ctx = new InitialDirContext(env);
            Attributes attrs = ctx.getAttributes(recordName, new String[]{"TXT"});
            Attribute txt = attrs.get("TXT");
            if (txt == null) return false;
            for (int i = 0; i < txt.size(); i++) {
                String value = String.valueOf(txt.get(i)).replace("\"", "");
                if (value.contains(expectedValue)) return true;
            }
        } catch (NamingException | RuntimeException e) {
            // No record yet, domain doesn't exist yet, or a transient DNS error —
            // all treated the same: "not verified yet", never a hard failure.
        }
        return false;
    }

    private String normalizeDomain(String raw) {
        if (raw == null) return null;
        String d = raw.trim().toLowerCase()
            .replaceFirst("^https?://", "")
            .replaceFirst("^www\\.", "")
            .replaceAll("/.*$", "");
        return d.isBlank() ? null : d;
    }

    private String randomToken() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private String str(Object o) { return o == null ? null : o.toString(); }
}
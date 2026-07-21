package com.patrick.fintech.loan_backend.config;

import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Seeds demo organizations, admin/officer logins, and loan product catalogs on first startup.
 * Deliberately does NOT create any demo borrowers, loans, or payments — those are fabricated
 * customer/financial data and have no place being auto-generated, even for local development.
 * Skipped entirely if organizations already exist (idempotent).
 * Roles are seeded by Flyway V1 migration — DataSeeder just finds them.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final OrganizationRepository orgRepo;
    private final UserRepository         userRepo;
    private final RoleRepository         roleRepo;
    private final PasswordEncoder        encoder;
    private final com.patrick.fintech.loan_backend.service.AccountingService accountingService;
    private final LoanProductRepository  loanProductRepo;

    @Override
    public void run(String... args) {
        // Production bootstrap path: creates exactly ONE real organization with ONE real admin
        // account, no demo borrowers/loans/payments, no published password. Takes priority over
        // demo seeding whenever BOOTSTRAP_ADMIN_EMAIL is set — which is how a real bank going
        // live for the first time should always start this app. Only runs if no orgs exist yet
        // at all, since it's meant for a brand-new deployment, not one that already has orgs.
        String bootstrapEmail = System.getenv("BOOTSTRAP_ADMIN_EMAIL");
        if (bootstrapEmail != null && !bootstrapEmail.isBlank()) {
            if (orgRepo.count() > 0) {
                log.info("BOOTSTRAP_ADMIN_EMAIL is set but organizations already exist — skipping "
                    + "bootstrap (this path is only for a brand-new database).");
                return;
            }
            bootstrapProductionOrg(bootstrapEmail);
            return;
        }

        log.warn("╔══════════════════════════════════════════════════════════════╗");
        log.warn("║  BOOTSTRAP_ADMIN_EMAIL is not set — seeding DEMO data with a   ║");
        log.warn("║  well-known password (admin@growthfinance.rw / Admin@1234)     ║");
        log.warn("║  that is PUBLISHED in this repo's own documentation. This is   ║");
        log.warn("║  fine for local development ONLY. Never let this run against   ║");
        log.warn("║  a real production database — set BOOTSTRAP_ADMIN_EMAIL and    ║");
        log.warn("║  BOOTSTRAP_ADMIN_PASSWORD instead to get one real org + one    ║");
        log.warn("║  real admin account with no demo data at all.                  ║");
        log.warn("╚══════════════════════════════════════════════════════════════╝");

        log.info("Running initial data seed (per-organization — existing orgs are left untouched)...");

        // Roles already seeded by Flyway V1 migration
        // Just look them up — create if missing (H2 dev profile)
        Role adminRole   = ensureRole("ADMIN",          "Full platform access");
        Role officerRole = ensureRole("LOAN_OFFICER",   "Approve and disburse loans");
        Role managerRole = ensureRole("MANAGER",        "Branch/portfolio management");

    boolean growthCreated = false, nobleCreated = false, infinityCreated = false;

    // ===== ORGANIZATION 1: Growth Finance Services Ltd (Rwanda) =====
    if (shouldSeed("growthfinance")) {
        growthCreated = true;
        Organization growth = orgRepo.save(Organization.builder()
            .name("Growth Finance Services Ltd").industry("Microfinance").country("RW")
            .defaultCurrency("RWF").timezone("Africa/Kigali").locale("en-RW")
            .primaryColor("#0D6B3E").accentColor("#F5A623")
            .website("https://growthfinance.rw")
            .contactEmail("info@growthfinance.rw").contactPhone("+250 788 000 000")
            .address("KG 7 Ave, Kigali, Rwanda").registrationNumber("REG-GFS-004")
            .tagline("Empowering Your Financial Growth")
            .mission("To provide accessible, affordable and transparent financial services to individuals and businesses across Rwanda.")
            .vision("To be Rwanda's most trusted financial partner, enabling prosperity for every client.")
            .foundedYear(2025)
            .facebookUrl("https://facebook.com/growthfinancerw").instagramUrl("https://instagram.com/growthfinancerw")
            .linkedinUrl("https://linkedin.com/company/growthfinancerw").twitterUrl("https://twitter.com/growthfinancerw")
            .whatsappUrl("https://wa.me/250788000000")
            .mapUrl("https://www.google.com/maps/embed?pb=!1m18!1m12!1m3!1d63800.15641867!2d30.0644!3d-1.9536!2m3!1f0!2f0!3f0!3m2!1i1024!2i768!4f13.1!3m3!1m2!1s0x19dca75a929d959f%3A0x0!2sKigali!5e0!3m2!1sen!2srw!4v1690000000000")
            .subscriptionTier(Organization.SubscriptionTier.PROFESSIONAL)
            .status(Organization.OrgStatus.ACTIVE)
            .maxUsers(100).maxActiveLoans(10000)
            .minLoanAmount(20000.0).maxLoanAmount(30_000_000.0)
            .subscribedAt(LocalDateTime.now()).subscriptionExpiresAt(LocalDateTime.now().plusYears(1))
            .build());

        String[] growthAdmin = adminCredsFor("growthfinance", "admin@growthfinance.rw", "Admin@1234");
        userRepo.save(makeUser("Eric Nshuti", growthAdmin[0], growthAdmin[1], adminRole, growth));
        accountingService.ensureChartOfAccounts(growth);
        userRepo.save(makeUser("Diane Uwase", "officer@growthfinance.rw", "Officer@1234", officerRole, growth));

        // No demo borrowers or loans are seeded — see class Javadoc.

        seedProduct(growth, "Personal Loan", "👤", Loan.LoanType.PERSONAL, 15.0, 50_000.0, 5_000_000.0, 3, 36,
            "Fast personal financing for any purpose — school fees, medical bills, home improvement.", 1);
        seedProduct(growth, "Business Loan", "🏢", Loan.LoanType.BUSINESS, 12.0, 500_000.0, 30_000_000.0, 6, 60,
            "Working capital and expansion financing for registered Rwandan businesses.", 2);
        seedProduct(growth, "Microfinance Loan", "💡", Loan.LoanType.MICROFINANCE, 18.0, 50_000.0, 1_000_000.0, 3, 12,
            "Small loans for micro-entrepreneurs and informal sector workers.", 3);

    } // end shouldSeed("growthfinance")

       
        log.info("");
        log.info("╔══════════════════════════════════════════════════════════════╗");
        log.info("║          LOANSAAS PRO — ORGANIZATION STATUS                 ║");
        log.info("╠══════════════════════════════════════════════════════════════╣");
        logOrgStatus("Growth Finance", "growthfinance", growthCreated);
        logOrgStatus("Noble Loan",     "nobleloan",     nobleCreated);
        logOrgStatus("Infinity Loan",  "infinityloan",  infinityCreated);
        log.info("╠══════════════════════════════════════════════════════════════╣");
        log.info("║  No demo borrowers, loans, or payments were created.         ║");
        log.info("╠══════════════════════════════════════════════════════════════╣");
        log.info("║  Swagger UI: http://localhost:8080/swagger-ui.html          ║");
        log.info("╚══════════════════════════════════════════════════════════════╝");
        log.info("");
    }

    /**
     * Real go-live path: exactly one organization, one admin user, and roles — nothing else.
     * No demo borrowers, loans, payments, or collection cases, and the admin password is never
     * logged or published anywhere; it's whatever the operator set in BOOTSTRAP_ADMIN_PASSWORD.
     */
    private void bootstrapProductionOrg(String adminEmail) {
        String adminPassword = System.getenv("BOOTSTRAP_ADMIN_PASSWORD");
        if (adminPassword == null || adminPassword.isBlank()) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_EMAIL is set but BOOTSTRAP_ADMIN_PASSWORD "
                + "is not — refusing to create an admin account with no password. Set both.");
        }
        if (adminPassword.length() < 12 || adminPassword.equalsIgnoreCase("Admin@1234")
                || adminPassword.equalsIgnoreCase("password") || adminPassword.equalsIgnoreCase("changeme")) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD is too weak or is a known demo "
                + "password. Use at least 12 characters and something not published anywhere.");
        }

        String orgName     = getenvOr("BOOTSTRAP_ORG_NAME", "My Financial Institution");
        String orgCountry  = getenvOr("BOOTSTRAP_ORG_COUNTRY", "RW");
        String orgCurrency = getenvOr("BOOTSTRAP_ORG_CURRENCY", "RWF");
        String adminName   = getenvOr("BOOTSTRAP_ADMIN_NAME", "Administrator");

        Role adminRole   = ensureRole("ADMIN",        "Full platform access");
        ensureRole("LOAN_OFFICER", "Approve and disburse loans");
        ensureRole("MANAGER",      "Branch/portfolio management");

        Organization org = orgRepo.save(Organization.builder()
            .name(orgName).country(orgCountry).defaultCurrency(orgCurrency)
            .timezone(getenvOr("BOOTSTRAP_ORG_TIMEZONE", "UTC")).locale("en")
            .primaryColor("#0D6B3E").accentColor("#F5A623")
            .subscriptionTier(Organization.SubscriptionTier.PROFESSIONAL)
            .status(Organization.OrgStatus.ACTIVE)
            .maxUsers(100).maxActiveLoans(10000)
            .minLoanAmount(1.0).maxLoanAmount(100_000_000.0)
            .subscribedAt(LocalDateTime.now()).subscriptionExpiresAt(LocalDateTime.now().plusYears(1))
            .build());

        userRepo.save(makeUser(adminName, adminEmail, adminPassword, adminRole, org));
        accountingService.ensureChartOfAccounts(org);

        log.info("╔══════════════════════════════════════════════════════════════╗");
        log.info("║  PRODUCTION BOOTSTRAP COMPLETE                                 ║");
        log.info("║  Organization: {}", orgName);
        log.info("║  Admin account: {} (password not logged)", adminEmail);
        log.info("║  No demo borrowers/loans/payments were created.                ║");
        log.info("╚══════════════════════════════════════════════════════════════╝");
    }

    private void logOrgStatus(String label, String slug, boolean justCreated) {
        String envKey = slug.toUpperCase().replace("-", "");
        boolean usedRealCreds = System.getenv("BOOTSTRAP_ADMIN_EMAIL_" + envKey) != null
            && !System.getenv("BOOTSTRAP_ADMIN_EMAIL_" + envKey).isBlank();
        if (justCreated && usedRealCreds) {
            log.info("║  {}: NEW — using your configured BOOTSTRAP_ADMIN_EMAIL_{}", label, envKey);
        } else if (justCreated) {
            log.info("║  {}: NEW — admin@{}.rw / officer@{}.rw", label, slug, slug);
            log.info("║  {}  Published demo password Admin@1234 / Officer@1234 — CHANGE IT NOW", " ".repeat(label.length()));
        } else if (orgAlreadyExists(slug)) {
            log.info("║  {}: already exists — password not reprinted (may already be changed)", label);
        }
    }

    /**
     * Real admin credentials for one specific organization, e.g.
     * BOOTSTRAP_ADMIN_EMAIL_NOBLELOAN / BOOTSTRAP_ADMIN_PASSWORD_NOBLELOAN.
     * Falls back to the published demo credentials only if neither is set —
     * this is what makes it safe to keep multiple orgs on one shared backend
     * without every one of them defaulting to the same public password.
     */
    private String[] adminCredsFor(String slug, String defaultEmail, String defaultPassword) {
        String envKey  = slug.toUpperCase().replace("-", "");
        String email    = System.getenv("BOOTSTRAP_ADMIN_EMAIL_" + envKey);
        String password = System.getenv("BOOTSTRAP_ADMIN_PASSWORD_" + envKey);
        if (email != null && !email.isBlank()) {
            if (password == null || password.isBlank()) {
                throw new IllegalStateException("BOOTSTRAP_ADMIN_EMAIL_" + envKey + " is set but "
                    + "BOOTSTRAP_ADMIN_PASSWORD_" + envKey + " is not — refusing to create an admin "
                    + "account with no password. Set both, or set neither to use the demo default.");
            }
            if (password.length() < 12 || password.equalsIgnoreCase("Admin@1234")
                    || password.equalsIgnoreCase("password") || password.equalsIgnoreCase("changeme")) {
                throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD_" + envKey + " is too weak "
                    + "or a known demo password. Use at least 12 characters and something not "
                    + "published anywhere.");
            }
            return new String[]{ email, password };
        }
        return new String[]{ defaultEmail, defaultPassword };
    }

    private String getenvOr(String key, String fallback) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? fallback : v;
    }

    /**
     * Which org this instance should seed. In the independent-per-org
     * deployment (see docker-compose.yml), each backend has SEED_ORG set to
     * its own slug, so it only ever seeds (and only ever holds) that one
     * org's data in its own database. Unset (e.g. local dev against one
     * shared database, or a shared production backend serving multiple
     * fronted orgs) seeds whichever of the three don't already exist yet —
     * this runs on every startup, so it must stay safe to call repeatedly
     * without creating duplicate organizations.
     */
    private boolean shouldSeed(String slug) {
        String target = System.getenv("SEED_ORG");
        boolean slugMatches = target == null || target.isBlank() || target.equalsIgnoreCase(slug);
        return slugMatches && !orgAlreadyExists(slug);
    }

    private boolean orgAlreadyExists(String slug) {
        return orgRepo.findAll().stream().anyMatch(o ->
            o.getName().toLowerCase().replace(" ", "").contains(slug.toLowerCase()));
    }

    private Role ensureRole(String name, String desc) {
        return roleRepo.findByName(name)
            .orElseGet(() -> roleRepo.save(new Role(null, name, desc)));
    }

    private User makeUser(String name, String email, String pw, Role role, Organization org) {
        User u = new User();
        u.setName(name); u.setEmail(email);
        u.setPassword(encoder.encode(pw));
        u.setRole(role); u.setOrganization(org);
        u.setStatus(User.UserStatus.ACTIVE);
        return u;
    }

    private void seedProduct(Organization org, String name, String icon, Loan.LoanType type,
                              double rate, double minAmount, double maxAmount,
                              int minTerm, int maxTerm, String description, int order) {
        loanProductRepo.save(LoanProduct.builder()
            .organization(org).name(name).icon(icon).loanType(type)
            .interestRate(rate).minAmount(minAmount).maxAmount(maxAmount)
            .minTermMonths(minTerm).maxTermMonths(maxTerm)
            .processingFeePercent(2.0).description(description)
            .active(true).displayOrder(order).build());
    }

}
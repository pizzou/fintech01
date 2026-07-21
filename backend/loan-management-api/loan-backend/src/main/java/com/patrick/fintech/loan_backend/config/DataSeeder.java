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
 * Seeds Growth Finance Services Ltd — organization record, admin/officer logins, and loan
 * product catalog — on first startup. Deliberately does NOT create any demo borrowers, loans,
 * or payments — those are fabricated customer/financial data and have no place being
 * auto-generated, even for local development.
 * Skipped entirely if the organization already exists (idempotent).
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
        // live for the first time should always start this app. Only runs if no org exists yet
        // at all, since it's meant for a brand-new deployment, not one that already has an org.
        String bootstrapEmail = System.getenv("BOOTSTRAP_ADMIN_EMAIL");
        if (bootstrapEmail != null && !bootstrapEmail.isBlank()) {
            if (orgRepo.count() > 0) {
                log.info("BOOTSTRAP_ADMIN_EMAIL is set but an organization already exists — skipping "
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

        log.info("Running initial data seed (safe to re-run — existing org is left untouched)...");

        // Roles already seeded by Flyway V1 migration
        // Just look them up — create if missing (H2 dev profile)
        Role adminRole   = ensureRole("ADMIN",          "Full platform access");
        Role officerRole = ensureRole("LOAN_OFFICER",   "Approve and disburse loans");
        Role managerRole = ensureRole("MANAGER",        "Branch/portfolio management");

        boolean growthCreated = false;

        // ===== Growth Finance Services Ltd (Rwanda) =====
        if (shouldSeed("growthfinance")) {
            growthCreated = true;
            Organization growth = orgRepo.save(Organization.builder()
                .name("Growth Finance Services Ltd").industry("Microfinance").country("RW")
                .defaultCurrency("RWF").timezone("Africa/Kigali").locale("en-RW")
                .primaryColor("#0D6B3E").accentColor("#F5A623")
                // REAL VALUES NEEDED — none of the below existed here before, or were
                // placeholder/fabricated content. Set these via PUT /api/organizations/me
                // once this org exists, or fill them in here before first deploy:
                // .website(...)            .contactEmail(...)       .contactPhone(...)
                // .address(...)            .registrationNumber(...) .foundedYear(...)
                // .facebookUrl(...)        .instagramUrl(...)       .linkedinUrl(...)
                // .twitterUrl(...)         .whatsappUrl(...)        .mapUrl(...)
                .tagline("Empowering Your Financial Growth")
                .mission("To provide accessible, affordable and transparent financial services to individuals and businesses across Rwanda.")
                .vision("To be Rwanda's most trusted financial partner, enabling prosperity for every client.")
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
        }

        log.info("");
        log.info("╔══════════════════════════════════════════════════════════════╗");
        log.info("║          LOANSAAS PRO — ORGANIZATION STATUS                 ║");
        log.info("╠══════════════════════════════════════════════════════════════╣");
        logOrgStatus("Growth Finance", "growthfinance", growthCreated);
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
     * BOOTSTRAP_ADMIN_EMAIL_GROWTHFINANCE / BOOTSTRAP_ADMIN_PASSWORD_GROWTHFINANCE.
     * Falls back to the published demo credentials only if neither is set.
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
     * Whether Growth Finance should be (re-)seeded. Safe to call on every startup —
     * returns false once the organization already exists, so this never creates
     * duplicates on a restart or redeploy.
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
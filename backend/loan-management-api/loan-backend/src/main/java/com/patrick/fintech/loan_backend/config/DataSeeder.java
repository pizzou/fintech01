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
    private final CurrencyRateRepository currencyRepo;
    private final PasswordEncoder        encoder;
    private final com.patrick.fintech.loan_backend.service.AccountingService accountingService;
    private final LoanProductRepository  loanProductRepo;

    @Override
    public void run(String... args) {
        if (orgRepo.count() > 0) {
            log.info("Data already seeded — skipping DataSeeder");
            return;
        }

        // Production bootstrap path: creates exactly ONE real organization with ONE real admin
        // account, no demo borrowers/loans/payments, no published password. Takes priority over
        // demo seeding whenever BOOTSTRAP_ADMIN_EMAIL is set — which is how a real bank going
        // live for the first time should always start this app.
        String bootstrapEmail = System.getenv("BOOTSTRAP_ADMIN_EMAIL");
        if (bootstrapEmail != null && !bootstrapEmail.isBlank()) {
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

        log.info("Running initial data seed...");

        // Roles already seeded by Flyway V1 migration
        // Just look them up — create if missing (H2 dev profile)
        Role adminRole   = ensureRole("ADMIN",          "Full platform access");
        Role officerRole = ensureRole("LOAN_OFFICER",   "Approve and disburse loans");
        Role managerRole = ensureRole("MANAGER",        "Branch/portfolio management");

        // Currency rates seeded by Flyway V2 — skip if already present
        if (currencyRepo.count() == 0) {
            seedCurrencyRates();
        }

    // ===== ORGANIZATION 1: Growth Finance Services Ltd (Rwanda) =====
    if (shouldSeed("growthfinance")) {
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
            .foundedYear(2020)
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

        userRepo.save(makeUser("Eric Nshuti", "admin@growthfinance.rw", "Admin@1234", adminRole, growth));
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

        // ===== ORGANIZATION 2: Noble Loan Solutions (Rwanda) =====
    if (shouldSeed("nobleloan")) {
        Organization noble = orgRepo.save(Organization.builder()
            .name("Noble Loan Solutions").industry("Microfinance").country("RW")
            .defaultCurrency("RWF").timezone("Africa/Kigali").locale("en-RW")
            .primaryColor("#1E3A8A").accentColor("#F59E0B")
            .website("https://nobleloan.rw")
            .contactEmail("info@nobleloan.rw").contactPhone("+250 788 111 222")
            .address("KN 4 Ave, Kigali, Rwanda").registrationNumber("REG-NLS-005")
            .tagline("Loans Built on Trust")
            .mission("To deliver honest, fast and fairly-priced credit to working Rwandans and small business owners.")
            .vision("To be the most dependable lending partner for every family and entrepreneur in Rwanda.")
            .foundedYear(2019)
            .facebookUrl("https://facebook.com/nobleloanrw").linkedinUrl("https://linkedin.com/company/nobleloanrw")
            .twitterUrl("https://twitter.com/nobleloanrw").whatsappUrl("https://wa.me/250788111222")
            .mapUrl("https://www.google.com/maps/embed?pb=!1m18!1m12!1m3!1d63800.15641867!2d30.0589!3d-1.9491!2m3!1f0!2f0!3f0!3m2!1i1024!2i768!4f13.1!3m3!1m2!1s0x19dca5d0e199999f%3A0x0!2sKigali!5e0!3m2!1sen!2srw!4v1690000000001")
            .subscriptionTier(Organization.SubscriptionTier.PROFESSIONAL)
            .status(Organization.OrgStatus.ACTIVE)
            .maxUsers(100).maxActiveLoans(10000)
            .minLoanAmount(20000.0).maxLoanAmount(25_000_000.0)
            .subscribedAt(LocalDateTime.now()).subscriptionExpiresAt(LocalDateTime.now().plusYears(1))
            .build());

        userRepo.save(makeUser("Patrick Habimana", "admin@nobleloan.rw", "Admin@1234", adminRole, noble));
        accountingService.ensureChartOfAccounts(noble);
        userRepo.save(makeUser("Grace Ingabire", "officer@nobleloan.rw", "Officer@1234", officerRole, noble));

        // No demo borrowers or loans are seeded — see class Javadoc.

        seedProduct(noble, "Personal Loan", "👤", Loan.LoanType.PERSONAL, 16.5, 30_000.0, 3_000_000.0, 3, 24,
            "Simple, honest personal lending with fast turnaround.", 1);
        seedProduct(noble, "Business Loan", "🏢", Loan.LoanType.BUSINESS, 13.0, 300_000.0, 20_000_000.0, 6, 48,
            "Grow your business with flexible working capital.", 2);
        seedProduct(noble, "Salary Advance", "💵", Loan.LoanType.SALARY_ADVANCE, 6.0, 20_000.0, 500_000.0, 1, 3,
            "Quick advance on your monthly salary — approved same day.", 3);

    } // end shouldSeed("nobleloan")

        // ===== ORGANIZATION 3: Infinity Loan Solutions (Rwanda) =====
    if (shouldSeed("infinityloan")) {
        Organization infinity = orgRepo.save(Organization.builder()
            .name("Infinity Loan Solutions").industry("Microfinance").country("RW")
            .defaultCurrency("RWF").timezone("Africa/Kigali").locale("en-RW")
            .primaryColor("#7C3AED").accentColor("#10B981")
            .website("https://infinityloan.rw")
            .contactEmail("info@infinityloan.rw").contactPhone("+250 788 333 444")
            .address("KG 11 Ave, Kigali, Rwanda").registrationNumber("REG-ILS-006")
            .tagline("Unlimited Possibilities, Real Support")
            .mission("To open up simple, flexible financing for micro-entrepreneurs and farmers who are often overlooked by traditional banks.")
            .vision("A Rwanda where every small business and farming household has fair access to capital.")
            .foundedYear(2023)
            .facebookUrl("https://facebook.com/infinityloanrw").instagramUrl("https://instagram.com/infinityloanrw")
            .whatsappUrl("https://wa.me/250788333444")
            .mapUrl("https://www.google.com/maps/embed?pb=!1m18!1m12!1m3!1d63800.15641867!2d30.0719!3d-1.9581!2m3!1f0!2f0!3f0!3m2!1i1024!2i768!4f13.1!3m3!1m2!1s0x19dca4d3a199999f%3A0x0!2sKigali!5e0!3m2!1sen!2srw!4v1690000000002")
            .subscriptionTier(Organization.SubscriptionTier.STARTER)
            .status(Organization.OrgStatus.TRIAL)
            .maxUsers(50).maxActiveLoans(5000)
            .minLoanAmount(15000.0).maxLoanAmount(15_000_000.0)
            .subscribedAt(LocalDateTime.now()).subscriptionExpiresAt(LocalDateTime.now().plusMonths(3))
            .build());

        userRepo.save(makeUser("Alice Umutoni", "admin@infinityloan.rw", "Admin@1234", adminRole, infinity));
        accountingService.ensureChartOfAccounts(infinity);
        userRepo.save(makeUser("Robert Mugisha", "officer@infinityloan.rw", "Officer@1234", officerRole, infinity));

        // No demo borrowers or loans are seeded — see class Javadoc.

        seedProduct(infinity, "Agricultural Loan", "🌾", Loan.LoanType.AGRICULTURAL, 9.0, 100_000.0, 5_000_000.0, 6, 24,
            "Seasonal farming and agribusiness finance for smallholder and commercial farmers.", 1);
        seedProduct(infinity, "Microfinance Loan", "💡", Loan.LoanType.MICROFINANCE, 19.0, 30_000.0, 800_000.0, 3, 12,
            "Accessible small loans for micro-entrepreneurs often overlooked by traditional banks.", 2);
        seedProduct(infinity, "SME Finance", "📦", Loan.LoanType.BUSINESS, 14.0, 500_000.0, 15_000_000.0, 6, 36,
            "Tailored finance for small and growing Rwandan businesses.", 3);
    } // end shouldSeed("infinityloan")

        log.info("");
        log.info("╔══════════════════════════════════════════════════════════════╗");
        log.info("║          LOANSAAS PRO — DEMO DATA SEEDED                    ║");
        log.info("╠══════════════════════════════════════════════════════════════╣");
        if (shouldSeed("growthfinance")) {
            log.info("║  Growth Finance: admin@growthfinance.rw / Admin@1234        ║");
            log.info("║                  officer@growthfinance.rw / Officer@1234    ║");
        }
        if (shouldSeed("nobleloan")) {
            log.info("║  Noble Loan:     admin@nobleloan.rw     / Admin@1234        ║");
            log.info("║                  officer@nobleloan.rw   / Officer@1234      ║");
        }
        if (shouldSeed("infinityloan")) {
            log.info("║  Infinity Loan:  admin@infinityloan.rw  / Admin@1234        ║");
            log.info("║                  officer@infinityloan.rw / Officer@1234     ║");
        }
        log.info("╠══════════════════════════════════════════════════════════════╣");
        log.info("║  No demo borrowers, loans, or payments were created.         ║");
        log.info("╠══════════════════════════════════════════════════════════════╣");
        log.info("║  Swagger UI: http://localhost:8080/swagger-ui.html          ║");
        log.info("╚══════════════════════════════════════════════════════════════╝");
        log.info("");
    }

    /**
     * Real go-live path: exactly one organization, one admin user, roles, and currency rates —
     * nothing else. No demo borrowers, loans, payments, or collection cases, and the admin
     * password is never logged or published anywhere; it's whatever the operator set in
     * BOOTSTRAP_ADMIN_PASSWORD.
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
        if (currencyRepo.count() == 0) seedCurrencyRates();

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

    private String getenvOr(String key, String fallback) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? fallback : v;
    }

    /**
     * Which org this instance should seed. In the independent-per-org
     * deployment (see docker-compose.yml), each backend has SEED_ORG set to
     * its own slug, so it only ever seeds (and only ever holds) that one
     * org's data in its own database. Unset (e.g. local dev against one
     * shared database) seeds all three, matching the original demo setup.
     */
    private boolean shouldSeed(String slug) {
        String target = System.getenv("SEED_ORG");
        return target == null || target.isBlank() || target.equalsIgnoreCase(slug);
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

    private void seedCurrencyRates() {
        String[][] rates = {
            {"USD","KES","130.5"}, {"USD","UGX","3730.0"}, {"USD","TZS","2480.0"},
            {"USD","NGN","1580.0"}, {"USD","GHS","12.3"}, {"USD","ZAR","18.7"},
            {"USD","RWF","1290.0"}, {"USD","ETB","57.5"}, {"USD","EUR","0.92"},
            {"USD","GBP","0.79"}, {"USD","INR","83.2"}, {"USD","AED","3.67"},
        };
        for (String[] r : rates) {
            if (currencyRepo.findByBaseCurrencyAndTargetCurrency(r[0], r[1]).isEmpty()) {
                currencyRepo.save(CurrencyRate.builder()
                    .baseCurrency(r[0]).targetCurrency(r[1]).rate(Double.parseDouble(r[2])).build());
            }
        }
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
package com.patrick.fintech.loan_backend.config;

import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

/**
 * Bootstraps this platform's organizations on first startup — Growth Finance
 * Services Ltd (the original tenant) and Noble Loan Solutions (added via
 * ensureNobleLoanSolutions, see its own doc for why it's structured
 * differently). Also bootstraps the platform-level SUPER_ADMIN account
 * (ensureSuperAdmin). Each of these three is independently idempotent, so
 * this class is safe to leave in place and redeploy indefinitely.
 *
 * No demo borrowers, loans, or extra staff accounts are created — this file
 * previously hardcoded fictional borrowers, their loans, and a demo Loan
 * Officer account with a published password. Publishing fixed credentials
 * in source is a real security exposure once this repo is anywhere (same
 * class of issue previously fixed for JWT_SECRET and DB_PASSWORD), and fake
 * borrower/loan records have no place in a live production database.
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
        // Independent of Growth Finance's org seeding below — so this keeps
        // working on every future redeploy without needing an empty database.
        ensureSuperAdmin();
        ensureNobleLoanSolutions();

        if (orgRepo.count() > 0) {
            log.info("Data already seeded — skipping DataSeeder");
            return;
        }

        log.info("Running initial bootstrap seed...");

        // Roles already seeded by Flyway V1 migration — just look them up (create if missing,
        // e.g. local H2 dev profile).
        Role adminRole   = ensureRole("ADMIN",        "Full platform access");
        Role officerRole = ensureRole("LOAN_OFFICER", "Approve and disburse loans");
        Role managerRole = ensureRole("MANAGER",      "Branch/portfolio management");

        // ===== ORGANIZATION: Growth Finance Services Ltd (Rwanda) =====
        // Edit these branding/contact defaults directly, or leave them — every field here is
        // also editable live from Dashboard → Settings → Website once the app is running.
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

        // Real admin account — from Render env vars, no hardcoded fallback for the password.
        // This only runs on the very first startup against an empty database; changing these
        // env vars later won't retroactively update an already-created admin.
        String bootstrapAdminEmail    = System.getenv("BOOTSTRAP_ADMIN_EMAIL");
        String bootstrapAdminPassword = System.getenv("BOOTSTRAP_ADMIN_PASSWORD");
        String bootstrapAdminName     = System.getenv("BOOTSTRAP_ADMIN_NAME");
        if (bootstrapAdminEmail == null || bootstrapAdminEmail.isBlank()
                || bootstrapAdminPassword == null || bootstrapAdminPassword.isBlank()) {
            throw new IllegalStateException(
                "BOOTSTRAP_ADMIN_EMAIL and BOOTSTRAP_ADMIN_PASSWORD must both be set — refusing " +
                "to create an admin account with a guessable default in production.");
        }
        String adminName = (bootstrapAdminName != null && !bootstrapAdminName.isBlank()) ? bootstrapAdminName : "Admin";
        userRepo.save(makeUser(adminName, bootstrapAdminEmail, bootstrapAdminPassword, adminRole, growth));
        accountingService.ensureChartOfAccounts(growth);

        // Real loan products — edit rates/limits directly here, or from Dashboard → Loan Products
        // once the app is running.
        seedProduct(growth, "Personal Loan", "👤", Loan.LoanType.PERSONAL, 10.0, "MONTHLY", 50_000.0, 5_000_000.0, 1, 12,
            "Fast personal financing for any purpose — school fees, medical bills, home improvement.", 1);
        seedProduct(growth, "Business Loan", "🏢", Loan.LoanType.BUSINESS, 12.0, "A", 500_000.0, 30_000_000.0, 1, 12,
            "Working capital and expansion financing for registered Rwandan businesses.", 2);
        seedProduct(growth, "Microfinance Loan", "💡", Loan.LoanType.MICROFINANCE, 18.0, "ANNUAL", 50_000.0, 1_000_000.0, 3, 12,
            "Small loans for micro-entrepreneurs and informal sector workers.", 3);

        log.info("");
        log.info("╔══════════════════════════════════════════════════════════════╗");
        log.info("║          LOANSAAS PRO — BOOTSTRAP COMPLETE                   ║");
        log.info("╠══════════════════════════════════════════════════════════════╣");
        log.info("║  {} — admin login: {}", growth.getName(), bootstrapAdminEmail);
        log.info("╚══════════════════════════════════════════════════════════════╝");
        log.info("");
    }

    /**
     * Second organization, onboarded the same way Growth Finance was —
     * hardcoded here since there's no website/domain for it yet (per your
     * plan to build that later). Idempotent on registrationNumber, so this
     * is safe to leave in place across every future deploy — it runs once,
     * then no-ops forever after.
     *
     * NOTE: this "edit code, redeploy" pattern is fine for a couple of
     * organizations you're onboarding personally, but it's not how you
     * should add organization #3 onward once you're taking on clients at
     * volume — use POST /api/super-admin/organizations instead (see
     * SuperAdminOrganizationController), which needs no redeploy at all.
     */
    private void ensureNobleLoanSolutions() {
        String regNumber = "REG-NLS-001";
        Role adminRole = ensureRole("ADMIN", "Full platform access");

        Organization noble = orgRepo.findByRegistrationNumber(regNumber).orElse(null);
        if (noble == null) {
            // Edit these branding/contact defaults directly — every field here is also
            // editable live from Dashboard → Settings → Website once staff can log in.
            // No `domain` set yet — the public site isn't built, so this org simply
            // won't resolve on any hostname until you assign one (see
            // SuperAdminOrganizationController.setDomain or the self-service
            // /api/organizations/me/domain flow) once the site exists.
            noble = orgRepo.save(Organization.builder()
                .name("Noble Loan Solutions").industry("Microfinance").country("RW")
                .defaultCurrency("RWF").timezone("Africa/Kigali").locale("en-RW")
                .primaryColor("#1B3A6B").accentColor("#D4A017")
                .registrationNumber(regNumber)
                .subscriptionTier(Organization.SubscriptionTier.TRIAL)
                .status(Organization.OrgStatus.PENDING_SETUP)
                .maxUsers(20).maxActiveLoans(1000)
                .minLoanAmount(20000.0).maxLoanAmount(10_000_000.0)
                .subscribedAt(LocalDateTime.now()).trialEndsAt(LocalDateTime.now().plusDays(14))
                .build());
            accountingService.ensureChartOfAccounts(noble);
        }

        // Independent of whether the org row already existed — this is the fix.
        // Previously this whole method returned as soon as the org existed, which
        // meant setting NOBLE_ADMIN_EMAIL/PASSWORD *after* the org was already
        // created (exactly what happened on Render) permanently skipped ever
        // creating the admin, even across further redeploys.
        if (userRepo.countByOrganization(noble) > 0) return;

        // Admin account — from env vars, same pattern as BOOTSTRAP_ADMIN_*. Skips
        // (doesn't fail startup) if unset, since this is a second org being added
        // to an already-running platform, not the initial required bootstrap.
        String email    = System.getenv("NOBLE_ADMIN_EMAIL");
        String password = System.getenv("NOBLE_ADMIN_PASSWORD");
        String name     = System.getenv("NOBLE_ADMIN_NAME");
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            log.info("NOBLE_ADMIN_EMAIL/NOBLE_ADMIN_PASSWORD not set — Noble Loan Solutions org has " +
                "no admin account yet. Set both and redeploy, or create the admin later via " +
                "POST /api/super-admin/organizations/{}/admin.", noble.getId());
            return;
        }
        String adminName = (name != null && !name.isBlank()) ? name : "Admin";
        userRepo.save(makeUser(adminName, email, password, adminRole, noble));

        log.info("Noble Loan Solutions bootstrapped — admin login: {}", email);
    }

    private Role ensureRole(String name, String desc) {
        return roleRepo.findByName(name)
            .orElseGet(() -> roleRepo.save(new Role(null, name, desc)));
    }

   
    private void ensureSuperAdmin() {
        Role superAdminRole = ensureRole("SUPER_ADMIN",
            "Platform owner — manages all organizations, billing, and domains across the whole SaaS");

        if (userRepo.existsByRole_NameAndOrganizationIsNull("SUPER_ADMIN")) return;

        String email    = System.getenv("SUPER_ADMIN_EMAIL");
        String password = System.getenv("SUPER_ADMIN_PASSWORD");
        String name     = System.getenv("SUPER_ADMIN_NAME");
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            log.info("SUPER_ADMIN_EMAIL/SUPER_ADMIN_PASSWORD not set — skipping super-admin bootstrap " +
                "(fine until you're ready to onboard another organization; set both and redeploy when you are).");
            return;
        }

        User superAdmin = new User();
        superAdmin.setName((name != null && !name.isBlank()) ? name : "Platform Admin");
        superAdmin.setEmail(email);
        superAdmin.setPassword(encoder.encode(password));
        superAdmin.setRole(superAdminRole);
        superAdmin.setOrganization(null); // deliberate — not tied to any single tenant
        superAdmin.setStatus(User.UserStatus.ACTIVE);
        userRepo.save(superAdmin);

        log.info("Super-admin account bootstrapped: {}", email);
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
                              double rate, String rateType, double minAmount, double maxAmount,
                              int minTerm, int maxTerm, String description, int order) {
        loanProductRepo.save(LoanProduct.builder()
            .organization(org).name(name).icon(icon).loanType(type)
            .interestRate(rate).interestRateType(rateType).minAmount(minAmount).maxAmount(maxAmount)
            .minTermMonths(minTerm).maxTermMonths(maxTerm)
            .processingFeePercent(2.0).description(description)
            .active(true).displayOrder(order).build());
    }
}
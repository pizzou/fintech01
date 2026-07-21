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

    if (orgRepo.count() > 0) {
        log.info("Database already seeded. Skipping.");
        return;
    }

    String bootstrapEmail = System.getenv("BOOTSTRAP_ADMIN_EMAIL");

    if (bootstrapEmail != null && !bootstrapEmail.isBlank()) {
        bootstrapProductionOrg(bootstrapEmail);
        return;
    }

    Role adminRole = ensureRole("ADMIN", "Full platform access");
    Role officerRole = ensureRole("LOAN_OFFICER", "Approve and disburse loans");
    ensureRole("MANAGER", "Branch/portfolio management");

    Organization growth = orgRepo.save(
        Organization.builder()
            .name("Growth Finance Services Ltd")
            .industry("Microfinance")
            .country("RW")
            .defaultCurrency("RWF")
            .timezone("Africa/Kigali")
            .locale("en-RW")
            .primaryColor("#0D6B3E")
            .accentColor("#F5A623")
            .website("https://growthfinance.rw")
            .contactEmail("info@growthfinance.rw")
            .contactPhone("+250788000000")
            .address("KG 7 Ave, Kigali")
            .registrationNumber("REG-GFS-004")
            .tagline("Empowering Your Financial Growth")
            .mission("To provide accessible, affordable financial services.")
            .vision("To become Rwanda's most trusted financial institution.")
            .foundedYear(2025)
            .subscriptionTier(Organization.SubscriptionTier.PROFESSIONAL)
            .status(Organization.OrgStatus.ACTIVE)
            .maxUsers(100)
            .maxActiveLoans(10000)
            .minLoanAmount(20000.0)
            .maxLoanAmount(30000000.0)
            .subscribedAt(LocalDateTime.now())
            .subscriptionExpiresAt(LocalDateTime.now().plusYears(1))
            .build()
    );

    userRepo.save(
        makeUser(
            "Eric Nshuti",
            "admin@growthfinance.rw",
            "Admin@1234",
            adminRole,
            growth
        )
    );

    userRepo.save(
        makeUser(
            "Diane Uwase",
            "officer@growthfinance.rw",
            "Officer@1234",
            officerRole,
            growth
        )
    );

    accountingService.ensureChartOfAccounts(growth);

    seedProduct(
        growth,
        "Personal Loan",
        "👤",
        Loan.LoanType.PERSONAL,
        15.0,
        50000,
        5000000,
        1,
        12,
        "Fast personal financing.",
        1
    );

    seedProduct(
        growth,
        "Business Loan",
        "🏢",
        Loan.LoanType.BUSINESS,
        12.0,
        500000,
        30000000,
        1,
        36,
        "Business financing.",
        2
    );

    seedProduct(
        growth,
        "Microfinance Loan",
        "💡",
        Loan.LoanType.MICROFINANCE,
        18.0,
        50000,
        1000000,
        3,
        12,
        "Microfinance products.",
        3
    );

    log.info("Growth Finance demo data created successfully.");
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

    
    
  

    private String getenvOr(String key, String fallback) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? fallback : v;
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
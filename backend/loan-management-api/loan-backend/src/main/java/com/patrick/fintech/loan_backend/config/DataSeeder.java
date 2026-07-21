package com.patrick.fintech.loan_backend.config;

import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;


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
        .address("KG 7 Ave, Kigali, Rwanda")
        .registrationNumber("REG-GFS-004")
        .tagline("Empowering Your Financial Growth")
        .mission("To provide accessible, affordable and transparent financial services to individuals and businesses across Rwanda.")
        .vision("To become Rwanda's most trusted financial institution, enabling financial inclusion and sustainable economic growth.")
        .foundedYear(2025)
        .facebookUrl("https://facebook.com/growthfinancerw")
        .instagramUrl("https://instagram.com/growthfinancerw")
        .linkedinUrl("https://linkedin.com/company/growthfinancerw")
        .twitterUrl("https://twitter.com/growthfinancerw")
        .whatsappUrl("https://wa.me/250788000000")
        .mapUrl("https://www.google.com/maps/embed?pb=!1m18!1m12!1m3!1d63800.15641867!2d30.0644!3d-1.9536!2m3!1f0!2f0!3f0!3m2!1i1024!2i768!4f13.1!3m3!1m2!1s0x19dca75a929d959f%3A0x0!2sKigali!5e0!3m2!1sen!2srw!4v1690000000000")
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
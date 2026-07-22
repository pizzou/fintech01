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
 * Seeds demo data on first startup.
 * Skipped if organizations already exist (idempotent).
 * Roles are seeded by Flyway V1 migration — DataSeeder just finds them.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final OrganizationRepository orgRepo;
    private final UserRepository         userRepo;
    private final BorrowerRepository     borrowerRepo;
    private final LoanRepository         loanRepo;
    private final PaymentRepository      paymentRepo;
    private final RoleRepository         roleRepo;
    private final CurrencyRateRepository currencyRepo;
    private final PasswordEncoder        encoder;
    private final com.patrick.fintech.loan_backend.service.CollectionsService collectionsService;
    private final com.patrick.fintech.loan_backend.service.AccountingService accountingService;
    private final LoanProductRepository  loanProductRepo;

    @Override
    public void run(String... args) {
        if (orgRepo.count() > 0) {
            log.info("Data already seeded — skipping DataSeeder");
            return;
        }

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

    // ===== ORGANIZATION: Growth Finance Services Ltd (Rwanda) =====
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

        // Admin credentials come from Render env vars (BOOTSTRAP_ADMIN_EMAIL,
        // BOOTSTRAP_ADMIN_PASSWORD, BOOTSTRAP_ADMIN_NAME) when set — falling back to a
        // demo default only if they're missing (e.g. local dev). This only runs on the very
        // first startup against an empty database; if you've already seeded once with the
        // old hardcoded admin@growthfinance.rw account, changing these env vars now won't
        // retroactively update it — you'd need a fresh database for this to take effect.
        String bootstrapAdminEmail    = System.getenv("BOOTSTRAP_ADMIN_EMAIL");
        String bootstrapAdminPassword = System.getenv("BOOTSTRAP_ADMIN_PASSWORD");
        String bootstrapAdminName     = System.getenv("BOOTSTRAP_ADMIN_NAME");
        String adminEmail    = (bootstrapAdminEmail    != null && !bootstrapAdminEmail.isBlank())    ? bootstrapAdminEmail    : "admin@growthfinance.rw";
        String adminPassword = (bootstrapAdminPassword != null && !bootstrapAdminPassword.isBlank()) ? bootstrapAdminPassword : "Admin@1234";
        String adminName     = (bootstrapAdminName     != null && !bootstrapAdminName.isBlank())     ? bootstrapAdminName     : "Admin";

        userRepo.save(makeUser(adminName, adminEmail, adminPassword, adminRole, growth));
        accountingService.ensureChartOfAccounts(growth);
        User growthOfficer = userRepo.save(makeUser("Diane Uwase", "officer@growthfinance.rw", "Officer@1234", officerRole, growth));

        Borrower jean = borrowerRepo.save(Borrower.builder()
            .organization(growth).firstName("Jean").lastName("Uwimana")
            .email("jean.uwimana@gmail.com").phone("+250-788-111-001")
            .nationalId("1198780012345678")
            .dateOfBirth(LocalDate.of(1990, 4, 12)).gender("M").nationality("RW")
            .employerName("Bank of Kigali").employmentType("PERMANENT").jobTitle("Accountant")
            .monthlyIncome(450000.0).monthlyExpenses(180000.0).creditScore(710)
            .city("Kigali").country("RW").kycStatus("VERIFIED")
            .status(Borrower.BorrowerStatus.ACTIVE)
            .bankName("Bank of Kigali").bankAccountNumber("40012345678").build());

        Borrower marie = borrowerRepo.save(Borrower.builder()
            .organization(growth).firstName("Marie").lastName("Mukamana")
            .email("marie.mukamana@gmail.com").phone("+250-788-111-002")
            .nationalId("1198880023456789")
            .dateOfBirth(LocalDate.of(1992, 9, 3)).gender("F").nationality("RW")
            .employerName("Self-employed").employmentType("SELF_EMPLOYED").jobTitle("Trader")
            .monthlyIncome(280000.0).monthlyExpenses(120000.0).creditScore(650)
            .city("Kigali").country("RW").kycStatus("VERIFIED")
            .status(Borrower.BorrowerStatus.ACTIVE).build());

        seedLoan(growth, jean,  growthOfficer, Loan.LoanType.PERSONAL, 2_000_000.0, "RWF", 24, LoanStatus.ACTIVE, 6);
        seedLoan(growth, marie, growthOfficer, Loan.LoanType.MICROFINANCE, 500_000.0, "RWF", 12, LoanStatus.PENDING, 0);
        seedLoan(growth, jean,  growthOfficer, Loan.LoanType.BUSINESS, 5_000_000.0, "RWF", 36, LoanStatus.OVERDUE, 4);

        seedProduct(growth, "Personal Loan", "👤", Loan.LoanType.PERSONAL, 15.0, 50_000.0, 5_000_000.0, 3, 36,
            "Fast personal financing for any purpose — school fees, medical bills, home improvement.", 1);
        seedProduct(growth, "Business Loan", "🏢", Loan.LoanType.BUSINESS, 12.0, 500_000.0, 30_000_000.0, 6, 60,
            "Working capital and expansion financing for registered Rwandan businesses.", 2);
        seedProduct(growth, "Microfinance Loan", "💡", Loan.LoanType.MICROFINANCE, 18.0, 50_000.0, 1_000_000.0, 3, 12,
            "Small loans for micro-entrepreneurs and informal sector workers.", 3);

    } // end shouldSeed("growthfinance")

        try {
            int cases = collectionsService.syncCasesFromOverdueLoans();
            log.info("Seeded {} collection case(s) from overdue/defaulted demo loans", cases);
        } catch (Exception e) {
            log.warn("Could not pre-seed collection cases: {}", e.getMessage());
        }

        log.info("");
        log.info("╔══════════════════════════════════════════════════════════════╗");
        log.info("║          LOANSAAS PRO — DEMO DATA SEEDED                    ║");
        log.info("╠══════════════════════════════════════════════════════════════╣");
        if (shouldSeed("growthfinance")) {
            log.info("║  Growth Finance admin login — see BOOTSTRAP_ADMIN_EMAIL      ║");
            log.info("║                  officer@growthfinance.rw / Officer@1234    ║");
        }
        log.info("╠══════════════════════════════════════════════════════════════╣");
        log.info("║  Swagger UI: http://localhost:8080/swagger-ui.html          ║");
        log.info("╚══════════════════════════════════════════════════════════════╝");
        log.info("");
    }

    /**
     * Which org this instance should seed. In the independent-per-org
     * deployment (see docker-compose.yml), each backend has SEED_ORG set to
     * its own slug, so it only ever seeds (and only ever holds) that one
     * org's data in its own database. Unset (e.g. local dev against one
     * shared database) seeds all configured orgs, matching the original demo setup.
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

    private void seedLoan(Organization org, Borrower borrower, User officer,
                           Loan.LoanType type, double amount, String currency,
                           int months, LoanStatus status, int paidMonths) {
        double rate = switch (type) {
            case MORTGAGE -> 8.5; case STUDENT -> 7.0; case AGRICULTURAL -> 9.0;
            case AUTO, ASSET_FINANCE -> 10.0; case BUSINESS -> 12.0;
            case PERSONAL -> 15.0; case EMERGENCY -> 18.0; case MICROFINANCE -> 20.0;
            default -> 14.0;
        };
        double mr      = rate / 100 / 12;
        double monthly = amount * (mr * Math.pow(1+mr, months)) / (Math.pow(1+mr, months)-1);
        double total   = monthly * months;
        double paid    = monthly * paidMonths;
        double balance = total - paid;

        Loan loan = loanRepo.save(Loan.builder()
            .referenceNumber(org.getCountry() + "-" + LocalDate.now().getYear()
                + "-" + String.format("%06d", (long)(Math.random() * 999999)))
            .organization(org).borrower(borrower).loanOfficer(officer)
            .loanType(type).repaymentFrequency(Loan.RepaymentFrequency.MONTHLY)
            .status(status).currency(currency)
            .amount(amount).interestRate(rate).durationMonths(months)
            .processingFee(r(amount * 0.02))
            .totalRepayable(r(total)).outstandingBalance(r(balance)).totalPaid(r(paid))
            .purpose(purposeFor(type)).collateralDescription(collateralFor(type))
            .riskScore(75.0).riskCategory("MEDIUM").debtToIncomeRatio(32.5)
            .creditScoreSnapshot(borrower.getCreditScore())
            .startDate(LocalDate.now().minusMonths(paidMonths + 1))
            .approvedAt(status != LoanStatus.PENDING ? LocalDate.now().minusMonths(paidMonths) : null)
            .disbursedAt((status == LoanStatus.ACTIVE || status == LoanStatus.PAID)
                ? LocalDate.now().minusMonths(paidMonths) : null)
            .maturityDate(LocalDate.now().plusMonths(months - paidMonths))
            .nextDueDate(status == LoanStatus.ACTIVE ? LocalDate.now().plusDays(12) : null)
            .lastPaymentDate(paidMonths > 0 ? LocalDate.now().minusDays(18) : null)
            .daysOverdue((status == LoanStatus.OVERDUE || status == LoanStatus.DEFAULTED)
                ? 15 + (int) (Math.random() * 80) : 0)
            .build());

        double bal = r(total);
        LocalDate due = loan.getStartDate().plusMonths(1);
        for (int i = 1; i <= months; i++) {
            double interest   = bal * mr;
            double principalC = monthly - interest;
            bal = Math.max(0, bal - principalC);
            boolean isPaid = i <= paidMonths;
            paymentRepo.save(Payment.builder()
                .paymentReference("PAY-" + loan.getReferenceNumber() + "-" + String.format("%03d", i))
                .loan(loan).organization(org).installmentNumber(i)
                .amount(r(monthly)).principalComponent(r(principalC))
                .interestComponent(r(interest)).dueDate(due)
                .paid(isPaid).amountPaid(isPaid ? r(monthly) : null)
                .paidDate(isPaid ? due.minusDays(2) : null)
                .outstandingAfter(r(bal))
                .status(isPaid ? Payment.PaymentStatus.COMPLETED : Payment.PaymentStatus.PENDING)
                .penalty(0.0).waivedAmount(0.0).isLate(false).daysLate(0)
                .build());
            due = due.plusMonths(1);
        }
    }

    private double r(double v) { return Math.round(v * 100.0) / 100.0; }

    private String purposeFor(Loan.LoanType t) {
        return switch (t) {
            case MORTGAGE -> "Purchase of residential property";
            case AUTO     -> "Motor vehicle purchase";
            case BUSINESS -> "Business expansion and working capital";
            case STUDENT  -> "University tuition fees";
            case PERSONAL -> "Home improvement and personal expenses";
            case EMERGENCY-> "Medical emergency expenses";
            default       -> "General financing";
        };
    }

    private String collateralFor(Loan.LoanType t) {
        return switch (t) {
            case MORTGAGE -> "Land title deed";
            case AUTO     -> "Motor vehicle logbook";
            case BUSINESS -> "Business assets and inventory";
            default       -> null;
        };
    }
}
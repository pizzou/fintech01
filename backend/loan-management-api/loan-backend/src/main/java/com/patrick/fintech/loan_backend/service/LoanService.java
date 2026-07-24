package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.*;
import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoanService {

    private final LoanRepository       loanRepo;
    private final OrganizationRepository orgRepo;
    private final PaymentRepository    paymentRepo;
    private final BorrowerRepository   borrowerRepo;
    private final RiskScoringService   riskService;
    private final NotificationService  notifService;
    private final MailService          mailService;
    private final SmsService             smsService;
    private final AuditLogRepository   auditRepo;
    private final WebhookService       webhookService;
    private final AuditService         auditService;
    private final LoanProductRepository loanProductRepo;
    private final AccountingService    accountingService;

    // Annual rates by loan type
    private static final Map<Loan.LoanType, Double> BASE_RATES = Map.ofEntries(
        Map.entry(Loan.LoanType.PERSONAL,       15.0),
        Map.entry(Loan.LoanType.MORTGAGE,        8.5),
        Map.entry(Loan.LoanType.AUTO,           10.0),
        Map.entry(Loan.LoanType.BUSINESS,       12.0),
        Map.entry(Loan.LoanType.STUDENT,         7.0),
        Map.entry(Loan.LoanType.EMERGENCY,      18.0),
        Map.entry(Loan.LoanType.ASSET_FINANCE,  11.0),
        Map.entry(Loan.LoanType.SALARY_ADVANCE,  5.0),
        Map.entry(Loan.LoanType.MICROFINANCE,   20.0),
        Map.entry(Loan.LoanType.AGRICULTURAL,    9.0),
        Map.entry(Loan.LoanType.TRADE_FINANCE,  13.0),
        Map.entry(Loan.LoanType.GROUP,          14.0)
    );

    @Transactional
    public Loan createLoan(LoanRequest req, Long organizationId, User createdBy) {
        Organization org = orgRepo.findById(organizationId)
            .orElseThrow(() -> new RuntimeException("Organization not found: " + organizationId));
        Borrower borrower = borrowerRepo.findById(req.getBorrowerId())
            .orElseThrow(() -> new RuntimeException("Borrower not found: " + req.getBorrowerId()));

        if (!borrower.getOrganization().getId().equals(organizationId))
            throw new RuntimeException("Borrower does not belong to this organization");

        Loan.LoanType requestedType = req.getLoanType() != null ? req.getLoanType() : Loan.LoanType.PERSONAL;
        LoanProduct product = loanProductRepo
            .findFirstByOrganization_IdAndLoanTypeAndActiveTrue(organizationId, requestedType)
            .orElse(null);

        if (product != null) {
            boolean tooLow  = req.getAmount() < product.getMinAmount();
            boolean tooHigh = product.getMaxAmount() != null && req.getAmount() > product.getMaxAmount();
            if (tooLow || tooHigh) {
                String range = product.getMaxAmount() != null
                    ? String.format("between %,.0f and %,.0f", product.getMinAmount(), product.getMaxAmount())
                    : String.format("at least %,.0f", product.getMinAmount());
                throw new RuntimeException(String.format("%s amount must be %s %s",
                    product.getName(), range, org.getDefaultCurrency()));
            }
            if (req.getDurationMonths() < product.getMinTermMonths() || req.getDurationMonths() > product.getMaxTermMonths()) {
                throw new RuntimeException(String.format(
                    "%s term must be between %d and %d months", product.getName(),
                    product.getMinTermMonths(), product.getMaxTermMonths()));
            }
        }

        double rate = req.getInterestRate() != null
            ? req.getInterestRate()
            : product != null ? product.getInterestRate() : BASE_RATES.getOrDefault(requestedType, 15.0);

        String rateType = req.getInterestRate() != null && req.getInterestRateType() != null
            ? req.getInterestRateType()
            : product != null ? product.getInterestRateType() : "ANNUAL";

        // Adjust rate for credit score
        if (borrower.getCreditScore() != null) rate = adjustRate(rate, borrower.getCreditScore(), rateType);

        double principal = req.getAmount();
        int    months    = req.getDurationMonths();
        double[] calc    = calcLoan(principal, rate, months, rateType);
        double feePct    = product != null && product.getProcessingFeePercent() != null ? product.getProcessingFeePercent() : 2.0;
        double procFee   = principal * (feePct / 100.0);
        double dti       = (borrower.getMonthlyIncome() != null && borrower.getMonthlyIncome() > 0)
                           ? (calc[0] / borrower.getMonthlyIncome()) * 100 : 0;

        Loan loan = Loan.builder()
            .referenceNumber(generateRef(org))
            .organization(org)
            .borrower(borrower)
            .loanOfficer(createdBy)
            .loanType(requestedType)
            .repaymentFrequency(req.getRepaymentFrequency() != null
                ? req.getRepaymentFrequency() : Loan.RepaymentFrequency.MONTHLY)
            .status(LoanStatus.PENDING)
            .amount(principal)
            .interestRate(rate)
            .interestRateType(rateType)
            .durationMonths(months)
            .currency(req.getCurrency() != null ? req.getCurrency() : org.getDefaultCurrency())
            .processingFee(round(procFee))
            .totalRepayable(round(calc[1]))
            .outstandingBalance(round(calc[1]))
            .totalPaid(0.0)
            .purpose(req.getPurpose())
            .notes(req.getNotes())
            .collateralDescription(req.getCollateralDescription())
            .collateralValue(req.getCollateralValue())
            .startDate(req.getStartDate() != null ? LocalDate.parse(req.getStartDate()) : LocalDate.now())
            .debtToIncomeRatio(round(dti))
            .creditScoreSnapshot(borrower.getCreditScore())
            .build();

        Loan saved = loanRepo.save(loan);

        // Async risk scoring
        scoreAsync(saved);

        audit(org, createdBy, "LOAN_CREATED", "LOAN", saved.getId().toString(),
              "Loan " + saved.getReferenceNumber() + " created for " + borrower.getFullName());

        return saved;
    }

    @Transactional
    public Loan approveLoan(Long loanId, User approvedBy, String notes) {
        Loan loan = getLoanForOrg(loanId, approvedBy.getOrganization().getId());
        if (loan.getStatus() != LoanStatus.PENDING && loan.getStatus() != LoanStatus.UNDER_REVIEW) {
            throw new RuntimeException("Cannot approve a loan that is " + loan.getStatus()
                + " — only loans that are Pending or Under Review can be approved."
                + (loan.getOutstandingBalance() != null && loan.getOutstandingBalance() <= 0.01 && loan.getStatus() == LoanStatus.PAID
                    ? " This loan has already been fully paid off." : ""));
        }

        loan.setStatus(LoanStatus.APPROVED);
        loan.setApprovedBy(approvedBy);
        loan.setApprovedAt(LocalDate.now());
        if (notes != null) loan.setInternalNotes(notes);
        Loan saved = loanRepo.save(loan);

        // Idempotency guard: if two approve requests race each other (double-click,
        // slow network + retry), both can pass the status check above before either
        // commits. Only generate the schedule if it doesn't already exist, so the
        // loser of the race is a no-op instead of a duplicate-key error.
        if (paymentRepo.findByLoanId(saved.getId()).isEmpty()) {
            generateRepaymentSchedule(saved);
        } else {
            log.warn("Repayment schedule already exists for loan {}, skipping regeneration", saved.getId());
        }
        audit(loan.getOrganization(), approvedBy, "LOAN_APPROVED", "LOAN",
              loanId.toString(), "Loan " + loan.getReferenceNumber() + " approved");
        try { mailService.sendLoanApproved(saved); } catch (Exception e) { log.warn("Notif failed", e); }
        try { smsService.sendLoanApproved(saved); } catch (Exception e) { log.warn("SMS failed", e); }
        notifyOfficer(saved, approvedBy, "Loan Approved",
            "Loan " + saved.getReferenceNumber() + " has been approved by " + approvedBy.getName() + ".", "success");
        webhookService.dispatch(loan.getOrganization(), "LOAN_APPROVED", saved);
        return saved;
    }

    @Transactional
    public Loan rejectLoan(Long loanId, User rejectedBy, String reason) {
        Loan loan = getLoanForOrg(loanId, rejectedBy.getOrganization().getId());
        if (loan.getStatus() != LoanStatus.PENDING && loan.getStatus() != LoanStatus.UNDER_REVIEW) {
            throw new RuntimeException("Cannot reject a loan that is " + loan.getStatus()
                + " — only loans that are Pending or Under Review can be rejected.");
        }
        loan.setStatus(LoanStatus.REJECTED);
        loan.setRejectionReason(reason);
        Loan saved = loanRepo.save(loan);
        audit(loan.getOrganization(), rejectedBy, "LOAN_REJECTED", "LOAN",
              loanId.toString(), "Reason: " + reason);
        try { mailService.sendLoanRejected(saved); } catch (Exception e) { log.warn("Notif failed", e); }
        try { smsService.sendLoanRejected(saved); } catch (Exception e) { log.warn("SMS failed", e); }
        notifyOfficer(saved, rejectedBy, "Loan Rejected",
            "Loan " + saved.getReferenceNumber() + " has been rejected by " + rejectedBy.getName()
                + (reason != null && !reason.isBlank() ? ". Reason: " + reason : "."), "warning");
        webhookService.dispatch(loan.getOrganization(), "LOAN_REJECTED", saved);
        return saved;
    }

    @Transactional
    public Loan disburseLoan(Long loanId, User officer, String disbursementMethod) {
        Loan loan = getLoanForOrg(loanId, officer.getOrganization().getId());
        if (loan.getStatus() != LoanStatus.APPROVED) throw new RuntimeException("Loan must be APPROVED before disbursement");
        loan.setStatus(LoanStatus.ACTIVE);
        loan.setDisbursedAt(LocalDate.now());
        loan.setDisbursedAmount(loan.getAmount());
        loan.setMaturityDate(LocalDate.now().plusMonths(loan.getDurationMonths()));
        loan.setNextDueDate(LocalDate.now().plusMonths(1));
        Loan saved = loanRepo.save(loan);
        audit(loan.getOrganization(), officer, "LOAN_DISBURSED", "LOAN",
              loanId.toString(), "Disbursed via " + disbursementMethod);
        accountingService.postDisbursement(saved);
        try { mailService.sendLoanDisbursed(saved, disbursementMethod); } catch (Exception e) { log.warn("Notif failed", e); }
        try { smsService.sendLoanDisbursed(saved, disbursementMethod); } catch (Exception e) { log.warn("SMS failed", e); }
        notifyOfficer(saved, officer, "Loan Disbursed",
            "Loan " + saved.getReferenceNumber() + " (" + saved.getCurrency() + " " + saved.getDisbursedAmount()
                + ") has been disbursed via " + disbursementMethod + ".", "success");
        webhookService.dispatch(loan.getOrganization(), "LOAN_DISBURSED", saved);
        return saved;
    }

    /** Notifies the loan's assigned officer in-app when someone else (a manager, another
     *  officer) changes the loan's status — a no-op if the officer isn't set or is the actor. */
    private void notifyOfficer(Loan loan, User actor, String title, String message, String type) {
        User officer = loan.getLoanOfficer();
        if (officer == null || (actor != null && officer.getId().equals(actor.getId()))) return;
        try {
            notifService.notifyUsers(java.util.List.of(officer), title, message, type, "/dashboard/loans/" + loan.getId());
        } catch (Exception e) {
            log.warn("In-app notification failed", e);
        }
    }

    @Transactional
    public Loan updateStatus(Long loanId, User user, LoanStatus newStatus, String notes) {
        Loan loan = getLoanForOrg(loanId, user.getOrganization().getId());
        LoanStatus current = loan.getStatus();

        switch (newStatus) {
            case UNDER_REVIEW -> {
                if (current != LoanStatus.PENDING)
                    throw new RuntimeException("Only a Pending loan can be moved to Under Review (currently " + current + ")");
            }
            case DEFAULTED -> {
                if (current != LoanStatus.ACTIVE && current != LoanStatus.OVERDUE)
                    throw new RuntimeException("Only an Active or Overdue loan can be marked Defaulted (currently " + current + ")");
            }
            case CLOSED -> {
                if (current != LoanStatus.PAID && current != LoanStatus.WRITTEN_OFF)
                    throw new RuntimeException("Only a fully Paid or Written-off loan can be Closed (currently " + current
                        + (loan.getOutstandingBalance() != null && loan.getOutstandingBalance() > 0.01
                            ? " — outstanding balance is " + loan.getOutstandingBalance() : "") + ")");
            }
            case RESTRUCTURED -> throw new RuntimeException(
                "Use the Restructure Loan action instead — it recalculates the repayment schedule correctly rather than just changing the label.");
            default -> throw new RuntimeException(
                "Use the dedicated Approve / Reject / Disburse actions for that change, not this generic status update.");
        }

        loan.setStatus(newStatus);
        if (notes != null && !notes.isBlank()) loan.setInternalNotes(notes);
        Loan saved = loanRepo.save(loan);
        audit(loan.getOrganization(), user, "LOAN_STATUS_CHANGED", "LOAN", loanId.toString(),
            current + " -> " + newStatus + (notes != null && !notes.isBlank() ? ": " + notes : ""));
        webhookService.dispatch(loan.getOrganization(), "LOAN_STATUS_CHANGED", saved);
        return saved;
    }

    public Page<Loan> getLoans(Organization org, int page, int size, String status, String type) {
        LoanStatus ls = (status != null && !status.isBlank()) ? LoanStatus.valueOf(status) : null;
        Loan.LoanType lt = (type != null && !type.isBlank()) ? Loan.LoanType.valueOf(type) : null;
        return loanRepo.findByFilters(org, ls, lt, PageRequest.of(page, size));
    }

    public Loan getLoanForOrg(Long loanId, Long orgId) {
        Loan loan = loanRepo.findById(loanId)
            .orElseThrow(() -> new RuntimeException("Loan not found: " + loanId));
        if (!loan.getOrganization().getId().equals(orgId))
            throw new RuntimeException("Access denied to loan: " + loanId);
        return loan;
    }

    public DashboardStats getDashboard(Organization org) {
        LocalDate firstOfMonth = LocalDate.now().withDayOfMonth(1);
        long overdueCount = paymentRepo.findByOrganization_IdAndPaidFalseAndDueDateBefore(
            org.getId(), LocalDate.now()).size();

        List<Map<String,Object>> typeBreakdown = loanRepo.getLoanTypeBreakdown(org).stream()
            .map(r -> { Map<String,Object> m = new LinkedHashMap<>();
                m.put("type", r[0]); m.put("count", r[1]); m.put("amount", r[2]); return m; })
            .collect(Collectors.toList());

        List<Loan> recent = loanRepo.findRecentByOrg(org, PageRequest.of(0, 8));

        return DashboardStats.builder()
            .totalLoans(loanRepo.countByOrganization(org))
            .pendingLoans(loanRepo.countByOrganizationAndStatus(org, LoanStatus.PENDING))
            .activeLoans(loanRepo.countByOrganizationAndStatus(org, LoanStatus.ACTIVE))
            .overdueLoans((long) overdueCount)
            .completedLoans(loanRepo.countByOrganizationAndStatus(org, LoanStatus.PAID))
            .defaultedLoans(loanRepo.countByOrganizationAndStatus(org, LoanStatus.DEFAULTED))
            .totalDisbursed(Optional.ofNullable(loanRepo.sumActivePrincipal(org)).orElse(0.0))
            .totalCollected(Optional.ofNullable(loanRepo.sumTotalCollected(org)).orElse(0.0))
            .outstandingBalance(Optional.ofNullable(loanRepo.sumOutstandingBalance(org)).orElse(0.0))
            .collectedThisMonth(Optional.ofNullable(paymentRepo.sumCollectedSince(org, firstOfMonth)).orElse(0.0))
            .totalBorrowers(borrowerRepo.countByOrganization(org))
            .latePaymentsCount(Optional.ofNullable(paymentRepo.countLatePayments(org)).orElse(0L))
            .loanTypeBreakdown(typeBreakdown)
            .recentLoans(recent)
            .build();
    }

    // ===== helpers =====
    private void generateRepaymentSchedule(Loan loan) {
        double principal = loan.getAmount() != null ? loan.getAmount() : 0;
        double rate      = loan.getInterestRate() != null ? loan.getInterestRate() : 0;
        String rateType  = loan.getInterestRateType() != null ? loan.getInterestRateType() : "ANNUAL";
        int    months    = loan.getDurationMonths() != null ? loan.getDurationMonths() : 1;
        double monthly   = calcLoan(principal, rate, months, rateType)[0];
        double balance   = loan.getTotalRepayable() != null ? loan.getTotalRepayable() : principal;
        double mRate     = "MONTHLY".equalsIgnoreCase(rateType) ? rate / 100 : rate / 100 / 12;

        LocalDate due = (loan.getStartDate() != null ? loan.getStartDate() : LocalDate.now()).plusMonths(1);

        for (int i = 1; i <= months; i++) {
            double interest   = balance * mRate;
            double principalC = monthly - interest;
            balance           = Math.max(0, balance - principalC);

            Payment p = Payment.builder()
                .paymentReference(generatePayRef(loan, i))
                .loan(loan).organization(loan.getOrganization())
                .installmentNumber(i).amount(round(monthly))
                .principalComponent(round(principalC)).interestComponent(round(interest))
                .dueDate(due).paid(false).penalty(0.0)
                .outstandingAfter(round(balance))
                .status(Payment.PaymentStatus.PENDING)
                .build();
            paymentRepo.save(p);
            due = due.plusMonths(1);
        }
        loan.setNextDueDate(loan.getStartDate() != null
            ? loan.getStartDate().plusMonths(1) : LocalDate.now().plusMonths(1));
        loanRepo.save(loan);
    }

    @Async
    public void scoreAsync(Loan loan) {
        try {
            var risk = riskService.score(loan);
            loan.setRiskScore(risk.getScore());
            loan.setRiskCategory(risk.getCategory());
            loanRepo.save(loan);
        } catch (Exception e) { log.warn("Risk scoring skipped: {}", e.getMessage()); }
    }

    private void audit(Organization org, User user, String action,
                       String entityType, String entityId, String desc) {
        auditService.log(org, user, action, entityType, entityId, desc);
    }

    private double adjustRate(double base, int creditScore, String rateType) {
        if ("MONTHLY".equalsIgnoreCase(rateType)) {
            // Scaled for a 6-10%/month range instead of the wider annual spread below —
            // e.g. an 8%/month base product lands excellent-credit borrowers at 6%,
            // good/fair credit stays at the product's own rate, weaker credit at +2.
            if (creditScore >= 750) return Math.max(6.0, base - 2.0);
            if (creditScore >= 650) return base;
            return Math.min(10.0, base + 2.0);
        }
        if (creditScore >= 800) return base - 2.0;
        if (creditScore >= 750) return base - 1.0;
        if (creditScore >= 700) return base;
        if (creditScore >= 650) return base + 1.0;
        return base + 3.0;
    }

    private double[] calcLoan(double principal, double rate, int months, String rateType) {
        double mr = "MONTHLY".equalsIgnoreCase(rateType) ? rate / 100 : rate / 100 / 12;
        if (mr == 0) return new double[]{principal / months, principal};
        double monthly = principal * (mr * Math.pow(1+mr, months)) / (Math.pow(1+mr, months)-1);
        return new double[]{monthly, monthly * months};
    }

    private double round(double v) { return Math.round(v * 100.0) / 100.0; }

   private String generateRef(Organization org) {

    String prefix = "RW";

    if (org != null &&
        org.getCountry() != null &&
        !org.getCountry().trim().isEmpty()) {
        prefix = org.getCountry().trim().toUpperCase();
    }

    String timestamp = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));

    return prefix + timestamp;
}

    private String generatePayRef(Loan loan, int installment) {
        return "PAY-" + loan.getReferenceNumber() + "-" + String.format("%03d", installment);
    }

    // Accessor for controller use
    public LoanRepository getLoanRepository() { return loanRepo; }

}

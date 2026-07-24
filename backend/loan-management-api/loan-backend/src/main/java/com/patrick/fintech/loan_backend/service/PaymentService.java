package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository  paymentRepo;
    private final LoanRepository     loanRepo;
    private final AuditLogRepository auditRepo;
    private final AuditService auditService;
    private final UserRepository     userRepo;
    private final NotificationService notifService;
    private final MailService         mailService;
    private final SmsService            smsService;
    private final WebhookService      webhookService;
    private final AccountingService   accountingService;

    @Transactional
    public Payment recordPayment(Long loanId, Double amount, String method,
                                  String txnId, String channel, String notes,
                                  User recordedBy) {
        Loan loan = loanRepo.findById(loanId)
            .orElseThrow(() -> new RuntimeException("Loan not found: " + loanId));

        // recordedBy is null for system-originated payments (e.g. a Flutterwave webhook
        // confirming a payment asynchronously, with no staff member "present" for it) —
        // the loan's organization is already established via other means in that case,
        // so we only need to enforce the org match when there IS a human actor to check.
        if (recordedBy != null && !loan.getOrganization().getId().equals(recordedBy.getOrganization().getId()))
            throw new RuntimeException("Access denied");

        if (loan.getStatus() != LoanStatus.ACTIVE && loan.getStatus() != LoanStatus.OVERDUE)
            throw new RuntimeException("Loan is not active (status: " + loan.getStatus() + ")");

        // Find earliest unpaid installment
        Optional<Payment> nextInstallmentOpt = paymentRepo.findByLoanId(loanId).stream()
            .filter(p -> !p.getPaid())
            .min(java.util.Comparator.comparing(Payment::getDueDate));

        boolean isLate = false;
        int daysLate   = 0;
        Payment installment = null;

        if (nextInstallmentOpt.isPresent()) {
            installment = nextInstallmentOpt.get();
            isLate      = LocalDate.now().isAfter(installment.getDueDate());
            if (isLate)
                daysLate = (int) java.time.temporal.ChronoUnit.DAYS
                    .between(installment.getDueDate(), LocalDate.now());
        }

        double penalty   = isLate ? amount * 0.02 * daysLate / 30 : 0;
        double effective = amount - penalty;
        double balance   = loan.getOutstandingBalance() != null ? loan.getOutstandingBalance() : 0;
        double newBalance = Math.max(0, balance - effective);

        // Mark installment paid
        if (installment != null) {
            installment.setPaid(true);
            installment.setPaidDate(LocalDate.now());
            installment.setAmountPaid(amount);
            installment.setPenalty(round(penalty));
            installment.setOutstandingAfter(round(newBalance));
            installment.setLate(isLate);
            installment.setDaysLate(daysLate);
            installment.setPaymentMethod(method);
            installment.setTransactionId(txnId);
            installment.setChannel(channel);
            installment.setNotes(notes);
            installment.setStatus(Payment.PaymentStatus.COMPLETED);
            installment.setPaymentReference(generateRef(loan));
            paymentRepo.save(installment);
        }

        // Update loan
        loan.setTotalPaid(round((loan.getTotalPaid() != null ? loan.getTotalPaid() : 0) + amount));
        loan.setOutstandingBalance(round(newBalance));
        loan.setLastPaymentDate(LocalDate.now());

        if (newBalance <= 0) {
            loan.setStatus(LoanStatus.PAID);
        } else {
            loan.setStatus(LoanStatus.ACTIVE);
            // Advance next due date
            if (installment != null)
                loan.setNextDueDate(installment.getDueDate().plusMonths(1));
        }
        loanRepo.save(loan);

        audit(loan.getOrganization(), recordedBy, "PAYMENT_RECORDED", "PAYMENT",
              installment != null ? installment.getId().toString() : "manual",
              "Payment of " + amount + " on loan " + loan.getReferenceNumber());

        try { mailService.sendPaymentConfirmation(loan, amount); } catch (Exception e) { log.warn("Notif failed", e); }
        try { smsService.sendPaymentConfirmed(loan, amount); } catch (Exception e) { log.warn("SMS failed", e); }
        if (loan.getLoanOfficer() != null && !loan.getLoanOfficer().getId().equals(recordedBy.getId())) {
            try {
                notifService.notifyUsers(java.util.List.of(loan.getLoanOfficer()), "Payment Received",
                    "A payment of " + loan.getCurrency() + " " + amount + " was recorded on loan "
                        + loan.getReferenceNumber() + " by " + recordedBy.getName() + ".",
                    "success", "/dashboard/loans/" + loan.getId());
            } catch (Exception e) { log.warn("In-app notification failed", e); }
        }
        webhookService.dispatch(loan.getOrganization(), "PAYMENT_MADE", loan);
        if (installment != null) accountingService.postPaymentReceived(installment);

        return installment;
    }

    public List<Payment> getLoanSchedule(Long loanId, Long orgId) {
        Loan loan = loanRepo.findById(loanId)
            .orElseThrow(() -> new RuntimeException("Loan not found"));
        if (!loan.getOrganization().getId().equals(orgId))
            throw new RuntimeException("Access denied");
        return paymentRepo.findByLoanId(loanId);
    }

    /** Nightly job: flag overdue loans */
    @Transactional
    public void markOverdueLoans(Long orgId) {
        List<Payment> overduePayments = paymentRepo
            .findByOrganization_IdAndPaidFalseAndDueDateBefore(orgId, LocalDate.now());
        for (Payment p : overduePayments) {
            Loan loan = p.getLoan();
            if (loan.getStatus() == LoanStatus.ACTIVE) {
                loan.setStatus(LoanStatus.OVERDUE);
                int days = (int) java.time.temporal.ChronoUnit.DAYS
                    .between(p.getDueDate(), LocalDate.now());
                loan.setDaysOverdue(Math.max(loan.getDaysOverdue() != null ? loan.getDaysOverdue() : 0, days));
                loanRepo.save(loan);
            }
        }
    }

    private double round(double v) { return Math.round(v * 100.0) / 100.0; }

    private String generateRef(Loan loan) {
        return "PAY-" + loan.getReferenceNumber() + "-" + System.currentTimeMillis() % 100000;
    }

    private void audit(Organization org, User user, String action,
                       String entityType, String entityId, String desc) {
        auditService.log(org, user, action, entityType, entityId, desc);
    }
}

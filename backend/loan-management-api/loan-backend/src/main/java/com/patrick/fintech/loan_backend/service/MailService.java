package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import jakarta.mail.internet.MimeMessage;

/**
 * All outbound email — the email counterpart to SmsService. Previously this lived inside
 * NotificationService mixed together with in-app notification creation (notifyUsers), which
 * made that class do two unrelated jobs. Split out so email templates live in one place the
 * same way SMS templates do.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${app.mail.from:noreply@loansaas.io}")
    private String from;

    @Value("${spring.mail.username:}")
    private String smtpUsername;

    @Value("${spring.mail.password:}")
    private String smtpPassword;

    /** Fails loudly at boot instead of silently swallowing every send() call later. Without
     *  this, "MAIL_ENABLED=true but MAIL_USERNAME/MAIL_PASSWORD unset" looks identical from the
     *  outside to "everything is fine" — borrowers just never get emails and nothing in the logs
     *  points at why. */
    @jakarta.annotation.PostConstruct
    void checkConfig() {
        if (mailEnabled && (smtpUsername == null || smtpUsername.isBlank()
                || smtpPassword == null || smtpPassword.isBlank())) {
            log.error("app.mail.enabled=true but MAIL_USERNAME/MAIL_PASSWORD are blank — every "
                + "borrower email (application received, approved, rejected, disbursed, document "
                + "status, restructuring, etc.) will fail SMTP auth silently. Set real values via "
                + "environment variables / your secrets manager, not this file.");
        }
    }

    @Async
    public void sendApplicationReceived(Loan loan) {
        if (!mailEnabled) { log.info("[EMAIL] Application received: {}", loan.getReferenceNumber()); return; }
        String to = loan.getBorrower().getEmail();
        if (to == null) return;
        send(to,
            "Loan Application Received",
            "<p>Dear " + loan.getBorrower().getFullName() + ",</p>" +
            "<p>We have successfully received your loan application.</p>" +
            "<p><strong>Reference Number:</strong> " + loan.getReferenceNumber() + "</p>" +
            "<p><strong>Current Status:</strong> Submitted</p>" +
            "<p>You can track your application from your borrower dashboard.</p>" +
            "<p>Thank you.<br/>Loan Management System</p>"
        );
    }

    @Async
    public void sendLoanUpdateComment(Loan loan, String message) {
        if (!mailEnabled) { log.info("[EMAIL] Loan update comment: {}", loan.getReferenceNumber()); return; }
        String to = loan.getBorrower().getEmail();
        if (to == null) return;
        send(to,
            "New Update on Your Application — " + loan.getReferenceNumber(),
            "<h2>New Message From Your Loan Officer</h2>" +
            "<p>There's a new update on your loan application <strong>" + loan.getReferenceNumber() + "</strong>:</p>" +
            "<blockquote style=\"border-left:3px solid #0D9488;margin:12px 0;padding:8px 16px;color:#374151;background:#f9fafb;\">"
                + message + "</blockquote>" +
            "<p>You can view the full details and respond from your application tracking page.</p>"
        );
    }

    @Async
    public void sendLoginOtp(User user, String code) {
        if (!mailEnabled) { log.info("[EMAIL] Login OTP for {}: {}", user.getEmail(), code); return; }
        send(user.getEmail(),
            "Your sign-in code: " + code,
            "<h2>Sign-in Verification Code</h2>" +
            "<p>Use this code to finish signing in:</p>" +
            "<p style=\"font-size:28px;font-weight:bold;letter-spacing:6px;\">" + code + "</p>" +
            "<p>This code expires in 5 minutes. If you didn't try to sign in, you can ignore this email — " +
            "your account is still safe, but consider changing your password if it happens again.</p>"
        );
    }

    @Async
    public void sendLoanApproved(Loan loan) {
        if (!mailEnabled) { log.info("[EMAIL] Loan approved: {}", loan.getReferenceNumber()); return; }
        String to = loan.getBorrower().getEmail();
        if (to == null) return;
        send(to,
            "Your Loan Has Been Approved — " + loan.getReferenceNumber(),
            "<h2>Congratulations!</h2>" +
            "<p>Your loan application <strong>" + loan.getReferenceNumber() + "</strong> has been approved.</p>" +
            "<p>Amount: <strong>" + loan.getCurrency() + " " + loan.getAmount() + "</strong></p>" +
            "<p>You will receive the funds shortly. Please review your repayment schedule.</p>"
        );
    }

    @Async
    public void sendLoanRejected(Loan loan) {
        if (!mailEnabled) { log.info("[EMAIL] Loan rejected: {}", loan.getReferenceNumber()); return; }
        String to = loan.getBorrower().getEmail();
        if (to == null) return;
        send(to,
            "Update on Your Loan Application — " + loan.getReferenceNumber(),
            "<h2>Application Update</h2>" +
            "<p>We regret to inform you that your loan application <strong>" +
            loan.getReferenceNumber() + "</strong> was not approved at this time.</p>" +
            "<p>Reason: " + (loan.getRejectionReason() != null ? loan.getRejectionReason() : "See your loan officer") + "</p>"
        );
    }

    @Async
    public void sendPaymentConfirmation(Loan loan, Double amount) {
        if (!mailEnabled) { log.info("[EMAIL] Payment confirmed: {} -> {}", loan.getReferenceNumber(), amount); return; }
        String to = loan.getBorrower().getEmail();
        if (to == null) return;
        send(to,
            "Payment Received — " + loan.getReferenceNumber(),
            "<h2>Payment Confirmed</h2>" +
            "<p>We have received your payment of <strong>" + loan.getCurrency() + " " + amount + "</strong> " +
            "on loan <strong>" + loan.getReferenceNumber() + "</strong>.</p>" +
            "<p>Outstanding balance: <strong>" + loan.getCurrency() + " " + loan.getOutstandingBalance() + "</strong></p>"
        );
    }

    @Async
    public void sendLoanDisbursed(Loan loan, String method) {
        if (!mailEnabled) { log.info("[EMAIL] Loan disbursed: {}", loan.getReferenceNumber()); return; }
        String to = loan.getBorrower().getEmail();
        if (to == null) return;
        send(to,
            "Funds Disbursed — " + loan.getReferenceNumber(),
            "<h2>Funds Sent Successfully</h2>" +
            "<p>Your loan <strong>" + loan.getReferenceNumber() + "</strong> has been disbursed.</p>" +
            "<p>Amount: <strong>" + loan.getCurrency() + " " + loan.getDisbursedAmount() + "</strong> via " + method + "</p>" +
            "<p>Your first payment is due on <strong>" + loan.getNextDueDate() + "</strong>. " +
            "You can track your loan from your borrower dashboard.</p>"
        );
    }

    @Async
    public void sendPaymentDueReminder(Loan loan) {
        if (!mailEnabled) { log.info("[EMAIL] Payment reminder: {}", loan.getReferenceNumber()); return; }
        String to = loan.getBorrower().getEmail();
        if (to == null) return;
        send(to,
            "Payment Due Reminder — " + loan.getReferenceNumber(),
            "<h2>Payment Reminder</h2>" +
            "<p>Your next payment on loan <strong>" + loan.getReferenceNumber() + "</strong> is due on <strong>"
            + loan.getNextDueDate() + "</strong>.</p>" +
            "<p>Please ensure your account has sufficient funds to avoid penalties.</p>"
        );
    }

    /** Distinct from the reminder above — this is for a payment that has already missed its
     *  due date, not one that's coming up. */
    @Async
    public void sendOverdueReminder(Loan loan, int daysOverdue) {
        if (!mailEnabled) { log.info("[EMAIL] Overdue reminder: {} ({} days)", loan.getReferenceNumber(), daysOverdue); return; }
        String to = loan.getBorrower().getEmail();
        if (to == null) return;
        send(to,
            "URGENT: Payment Overdue — " + loan.getReferenceNumber(),
            "<h2 style=\"color:#dc2626;\">Payment Overdue</h2>" +
            "<p>Your loan <strong>" + loan.getReferenceNumber() + "</strong> has a payment that is now "
            + "<strong>" + daysOverdue + " day(s) overdue</strong>.</p>" +
            "<p>Outstanding balance: <strong>" + loan.getCurrency() + " " + loan.getOutstandingBalance() + "</strong></p>" +
            "<p>Please make a payment as soon as possible to avoid further penalties, or contact us if you're facing difficulty.</p>"
        );
    }

    @Async
    public void sendLoanRestructured(Loan loan, String reason) {
        if (!mailEnabled) { log.info("[EMAIL] Loan restructured: {}", loan.getReferenceNumber()); return; }
        String to = loan.getBorrower().getEmail();
        if (to == null) return;
        send(to,
            "Your Loan Terms Have Changed — " + loan.getReferenceNumber(),
            "<h2>Loan Restructured</h2>" +
            "<p>The terms on your loan <strong>" + loan.getReferenceNumber() + "</strong> have been updated:</p>" +
            "<p>New term: <strong>" + loan.getDurationMonths() + " months</strong> at <strong>" + loan.getInterestRate() + "%</strong></p>" +
            (reason != null && !reason.isBlank() ? "<p>Reason: " + reason + "</p>" : "") +
            "<p>Your repayment schedule has been recalculated — please review it from your borrower dashboard.</p>"
        );
    }

    @Async
    public void sendLoanWrittenOff(Loan loan, String reason) {
        if (!mailEnabled) { log.info("[EMAIL] Loan written off: {}", loan.getReferenceNumber()); return; }
        String to = loan.getBorrower().getEmail();
        if (to == null) return;
        send(to,
            "Update on Your Loan — " + loan.getReferenceNumber(),
            "<h2>Account Status Update</h2>" +
            "<p>Your loan <strong>" + loan.getReferenceNumber() + "</strong> has been written off by " + org(loan) + ".</p>" +
            (reason != null && !reason.isBlank() ? "<p>Reason: " + reason + "</p>" : "") +
            "<p>Please contact us if you have questions about what this means for your account.</p>"
        );
    }

    @Async
    public void sendMoratoriumGranted(Loan loan, int pauseMonths, String reason) {
        if (!mailEnabled) { log.info("[EMAIL] Moratorium granted: {}", loan.getReferenceNumber()); return; }
        String to = loan.getBorrower().getEmail();
        if (to == null) return;
        send(to,
            "Payment Pause Approved — " + loan.getReferenceNumber(),
            "<h2>Payment Moratorium Granted</h2>" +
            "<p>Your upcoming payments on loan <strong>" + loan.getReferenceNumber() + "</strong> have been paused for "
            + "<strong>" + pauseMonths + " month(s)</strong>.</p>" +
            (reason != null && !reason.isBlank() ? "<p>Reason: " + reason + "</p>" : "") +
            "<p>Your next due date is now <strong>" + loan.getNextDueDate() + "</strong>. No action is needed from you during the pause.</p>"
        );
    }

    private String org(Loan loan) {
        return loan.getOrganization() != null && loan.getOrganization().getName() != null
            ? loan.getOrganization().getName() : "our team";
    }

    @Async
    public void sendPasswordResetEmail(User user, String resetLink) {
        if (!mailEnabled) {
            log.info("[EMAIL] Password reset for {}: {}", user.getEmail(), resetLink);
            return;
        }
        send(user.getEmail(),
            "Reset Your LoanSaaS Pro Password",
            "<h2>Password Reset Request</h2>" +
            "<p>Hi " + (user.getName() != null ? user.getName() : "") + ",</p>" +
            "<p>Click the link below to reset your password. This link expires in 1 hour.</p>" +
            "<p><a href=\"" + resetLink + "\" style=\"background:#0D9488;color:#fff;padding:12px 24px;" +
            "border-radius:8px;text-decoration:none;font-weight:bold;\">Reset Password</a></p>" +
            "<p>If you did not request a password reset, please ignore this email.</p>"
        );
    }

    // ---- KYC document verification emails ----

    @Async
    public void sendBorrowerWelcome(Borrower borrower) {
        if (!mailEnabled) { log.info("[EMAIL] Borrower profile created: {}", borrower.getEmail()); return; }
        if (borrower.getEmail() == null) return;
        send(borrower.getEmail(),
            "Your Profile Has Been Created",
            "<h2>Welcome</h2>" +
            "<p>Dear " + borrower.getFullName() + ",</p>" +
            "<p>A borrower profile has been created for you. Our team will reach out if we need any further information from you.</p>" +
            "<p>Thank you.<br/>Loan Management System</p>"
        );
    }

    @Async
    public void sendDocumentVerified(Borrower borrower, String documentType) {
        if (!mailEnabled) { log.info("[EMAIL] Document verified: {} for {}", documentType, borrower.getEmail()); return; }
        if (borrower.getEmail() == null) return;
        send(borrower.getEmail(),
            "Document Verified: " + humanizeDocType(documentType),
            "<h2>Document Verified</h2>" +
            "<p>Dear " + borrower.getFullName() + ",</p>" +
            "<p>Your <strong>" + humanizeDocType(documentType) + "</strong> has been verified. No further action is needed for this document.</p>" +
            "<p>You can check your overall application status from your borrower dashboard.</p>"
        );
    }

    @Async
    public void sendDocumentRejected(Borrower borrower, String documentType, String reason) {
        if (!mailEnabled) { log.info("[EMAIL] Document rejected: {} for {}", documentType, borrower.getEmail()); return; }
        if (borrower.getEmail() == null) return;
        send(borrower.getEmail(),
            "Action Needed: " + humanizeDocType(documentType) + " Rejected",
            "<h2>Document Rejected</h2>" +
            "<p>Dear " + borrower.getFullName() + ",</p>" +
            "<p>Your <strong>" + humanizeDocType(documentType) + "</strong> could not be accepted." +
            (reason != null && !reason.isBlank() ? " Reason: " + reason : "") + "</p>" +
            "<p>Please upload a corrected document from your application tracking page.</p>"
        );
    }

    @Async
    public void sendDocumentReplacementRequested(Borrower borrower, String documentType, String note) {
        if (!mailEnabled) { log.info("[EMAIL] Document replacement requested: {} for {}", documentType, borrower.getEmail()); return; }
        if (borrower.getEmail() == null) return;
        send(borrower.getEmail(),
            "Please Re-upload: " + humanizeDocType(documentType),
            "<h2>Replacement Document Requested</h2>" +
            "<p>Dear " + borrower.getFullName() + ",</p>" +
            "<p>We need a new copy of your <strong>" + humanizeDocType(documentType) + "</strong>." +
            (note != null && !note.isBlank() ? " " + note : "") + "</p>" +
            "<p>Please upload it from your application tracking page as soon as possible to avoid delays.</p>"
        );
    }

    private String humanizeDocType(String type) {
        if (type == null) return "document";
        String[] parts = type.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
        return sb.toString().trim();
    }

    private void send(String to, String subject, String html) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, true, "UTF-8");
            h.setFrom(from);
            h.setTo(to);
            h.setSubject(subject);
            h.setText(html, true);
            mailSender.send(msg);
        } catch (Exception e) {
            log.warn("Email send failed to {}: {}", to, e.getMessage());
        }
    }
}
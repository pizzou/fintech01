package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(
    name = "payments",
    indexes = {
        @Index(name = "idx_payment_loan", columnList = "loan_id"),
        @Index(name = "idx_payment_due", columnList = "due_date"),
        @Index(name = "idx_payment_paid_date", columnList = "paid_date"),
        @Index(name = "idx_payment_status", columnList = "status"),
        @Index(name = "idx_payment_org", columnList = "organization_id"),
        @Index(name = "idx_payment_transaction", columnList = "transaction_id")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    // ============================================================
    // IDENTITY
    // ============================================================

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Internal/public payment reference.
     *
     * Example:
     * PAY-20260730-000456
     */
    @Column(
        name = "payment_reference",
        unique = true,
        length = 100
    )
    private String paymentReference;

    // ============================================================
    // RELATIONSHIPS
    // ============================================================

    /**
     * Loan to which this payment belongs.
     *
     * Kept LAZY because payments are frequently loaded through
     * a loan and we do not want every payment API request to
     * recursively load the entire loan graph.
     */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "loan_id",
        nullable = false
    )
    private Loan loan;

    /**
     * Tenant / organization.
     */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "organization_id",
        nullable = false
    )
    private Organization organization;

    /**
     * User who recorded the payment.
     */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by")
    private User recordedBy;

    // ============================================================
    // INSTALLMENT INFORMATION
    // ============================================================

    /**
     * Installment number in the repayment schedule.
     *
     * Example:
     * 1, 2, 3, 4...
     */
    @Column(name = "installment_number")
    private Integer installmentNumber;

    /**
     * Scheduled installment amount.
     */
    @Column(name = "amount")
    private Double amount;

    /**
     * Principal portion scheduled for this installment.
     */
    @Column(name = "principal_component")
    private Double principalComponent;

    /**
     * Interest portion scheduled for this installment.
     */
    @Column(name = "interest_component")
    private Double interestComponent;

    /**
     * Actual amount paid by the borrower.
     */
    @Column(name = "amount_paid")
    private Double amountPaid;

    /**
     * Penalty charged.
     */
    @Column(name = "penalty")
    @Builder.Default
    private Double penalty = 0.0;

    /**
     * Amount waived by institution.
     */
    @Column(name = "waived_amount")
    @Builder.Default
    private Double waivedAmount = 0.0;

    /**
     * Loan outstanding balance after this payment.
     */
    @Column(name = "outstanding_after")
    private Double outstandingAfter;

    // ============================================================
    // PAYMENT STATUS
    // ============================================================

    /**
     * Whether the scheduled installment has been fully paid.
     */
    @Column(name = "paid")
    @Builder.Default
    private Boolean paid = false;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "status",
        length = 30
    )
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    // ============================================================
    // DATES
    // ============================================================

    /**
     * Scheduled payment date.
     */
    @Column(name = "due_date")
    private LocalDate dueDate;

    /**
     * Actual date payment was made.
     */
    @Column(name = "paid_date")
    private LocalDate paidDate;

    /**
     * Number of days late.
     */
    @Column(name = "days_late")
    @Builder.Default
    private Integer daysLate = 0;

    /**
     * Whether payment was made late.
     */
    @Column(name = "is_late")
    @Builder.Default
    private boolean isLate = false;

    // ============================================================
    // PAYMENT CHANNEL
    // ============================================================

    /**
     * Payment method.
     *
     * Examples:
     * MOBILE_MONEY
     * BANK_TRANSFER
     * CASH
     * CARD
     * GATEWAY
     */
    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    /**
     * Internal/external transaction ID.
     */
    @Column(name = "transaction_id", length = 150)
    private String transactionId;

    /**
     * External provider reference.
     */
    @Column(name = "external_reference", length = 150)
    private String externalReference;

    /**
     * Gateway response/reference.
     */
    @Column(
        name = "gateway_response",
        columnDefinition = "TEXT"
    )
    private String gatewayResponse;

    /**
     * Channel used to make the payment.
     *
     * Examples:
     * MTN_MOMO
     * AIRTEL_MONEY
     * BANK
     * CASHIER
     */
    @Column(name = "channel", length = 50)
    private String channel;

    // ============================================================
    // AUDIT / NOTES
    // ============================================================

    @Column(
        name = "notes",
        columnDefinition = "TEXT"
    )
    private String notes;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * Time payment was verified by staff/system.
     */
    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    // ============================================================
    // LIFECYCLE
    // ============================================================

    @PrePersist
    protected void onCreate() {

        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        if (paid == null) {
            paid = false;
        }

        if (penalty == null) {
            penalty = 0.0;
        }

        if (waivedAmount == null) {
            waivedAmount = 0.0;
        }

        if (daysLate == null) {
            daysLate = 0;
        }

        if (status == null) {
            status = PaymentStatus.PENDING;
        }

        /*
         * Keep isLate and daysLate consistent.
         */
        if (daysLate > 0) {
            isLate = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {

        /*
         * Keep isLate synchronized with daysLate.
         */
        if (daysLate != null && daysLate > 0) {
            isLate = true;
        }
    }

    // ============================================================
    // ENUM
    // ============================================================

    public enum PaymentStatus {

        /**
         * Payment/scheduled installment created but not completed.
         */
        PENDING,

        /**
         * Fully completed payment.
         */
        COMPLETED,

        /**
         * Payment failed.
         */
        FAILED,

        /**
         * Previously completed payment was reversed.
         */
        REVERSED,

        /**
         * Some but not all of the installment was paid.
         */
        PARTIALLY_PAID
    }
}

package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(
    name = "credit_bureau_checks",
    indexes = {
        @Index(name = "idx_cbc_borrower", columnList = "borrower_id"),
        @Index(name = "idx_cbc_org", columnList = "organization_id"),
        @Index(name = "idx_cbc_reference", columnList = "reference"),
        @Index(name = "idx_cbc_external_reference", columnList = "external_reference"),
        @Index(name = "idx_cbc_created_at", columnList = "created_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreditBureauCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "borrower_id", nullable = false)
    private Borrower borrower;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    /**
     * Internal unique reference for this bureau operation.
     *
     * Example:
     * CRB-RW-550e8400-e29b-41d4-a716-446655440000
     */
    @Column(
        nullable = false,
        unique = true,
        length = 100
    )
    private String reference;

    /**
     * Idempotency reference for operations such as loan reporting.
     *
     * Example:
     * DISBURSE-LOAN-2026-000123
     */
    @Column(
        name = "external_reference",
        unique = true,
        length = 150
    )
    private String externalReference;

    /**
     * Provider name.
     *
     * Example:
     * TRANSUNION_RW
     * CRB_AFRICA
     */
    @Column(length = 100)
    private String provider;

    /**
     * National ID used for the bureau inquiry.
     */
    @Column(name = "national_id_checked", length = 100)
    private String nationalIdChecked;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private CheckStatus status = CheckStatus.PENDING;

    /**
     * Provider-side request/report ID.
     */
    @Column(name = "provider_request_id", length = 150)
    private String providerRequestId;

    /**
     * Credit score returned by the provider.
     */
    private Integer creditScore;

    /**
     * EXCELLENT, GOOD, FAIR, POOR, VERY_POOR
     */
    @Column(length = 50)
    private String riskGrade;

    private Integer activeFacilities;

    private Integer delinquentAccounts;

    /**
     * Monetary values should use BigDecimal in production.
     */
    @Column(
        precision = 19,
        scale = 2
    )
    private BigDecimal totalOutstandingDebt;

    @Column(
        precision = 19,
        scale = 2
    )
    private BigDecimal totalMonthlyObligations;

    private Boolean hasDefaultHistory;

    private Boolean hasActiveListing;

    @Column(length = 500)
    private String listingReason;

    /**
     * Raw provider response.
     *
     * Be careful with sensitive data.
     * Consider encryption/redaction depending on your
     * compliance requirements.
     */
    @Column(
        name = "raw_response",
        columnDefinition = "TEXT"
    )
    private String rawResponse;

    @Column(length = 150)
    private String requestedBy;

    @Column(
        name = "failure_reason",
        length = 1000
    )
    private String failureReason;

    /**
     * Number of provider attempts.
     */
    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private Integer attemptCount = 0;

    /**
     * Last time a provider request was attempted.
     */
    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    /**
     * When the operation completed.
     */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /**
     * Bureau report validity period.
     */
    private LocalDateTime expiresAt;

    @PrePersist
    protected void onCreate() {

        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        if (status == null) {
            status = CheckStatus.PENDING;
        }

        if (attemptCount == null) {
            attemptCount = 0;
        }

        if (expiresAt == null) {
            expiresAt = createdAt.plusDays(90);
        }
    }

    public boolean isExpired() {
        return expiresAt != null
            && expiresAt.isBefore(LocalDateTime.now());
    }

    public boolean isSuccessful() {
        return status == CheckStatus.COMPLETED;
    }

    public boolean isFailed() {
        return status == CheckStatus.FAILED;
    }

    public boolean isPending() {
        return status == CheckStatus.PENDING
            || status == CheckStatus.PROCESSING;
    }

    public enum CheckStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
        NO_RECORD_FOUND
    }
}


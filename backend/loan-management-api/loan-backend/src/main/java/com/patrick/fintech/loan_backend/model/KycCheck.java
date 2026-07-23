package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "kyc_checks")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KycCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * Borrower
     */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "borrower_id", nullable = false)
    private Borrower borrower;

    /*
     * Organization
     */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    /*
     * KYC Type
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CheckType checkType;

    /*
     * Result
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CheckResult result;

    /*
     * Overall Risk
     */
    @Enumerated(EnumType.STRING)
    private RiskLevel riskLevel;

    /*
     * Provider
     * SumSub
     * Smile Identity
     * Veriff
     * Onfido
     * Trulioo
     * Internal
     */
    private String provider;

    /*
     * Provider transaction id
     */
    private String providerReference;

    /*
     * Confidence score
     */
    private Double matchScore;

    /*
     * National ID number verified?
     */
    private Boolean idVerified;

    /*
     * Face matched?
     */
    private Boolean faceMatched;

    /*
     * Liveness detection
     */
    private Boolean livenessPassed;

    /*
     * Address verified?
     */
    private Boolean addressVerified;

    /*
     * PEP Result
     */
    private Boolean pepMatch;

    /*
     * Sanctions Result
     */
    private Boolean sanctionsMatch;

    /*
     * Adverse media result
     */
    private Boolean adverseMediaMatch;

    /*
     * Duplicate customer?
     */
    private Boolean duplicateCustomer;

    /*
     * Fraud suspected?
     */
    private Boolean fraudDetected;

    /*
     * AML Risk Score
     */
    private Integer amlRiskScore;

    /*
     * Notes
     */
    @Column(length = 2000)
    private String notes;

    /*
     * Raw provider JSON. No @Lob on purpose — see BorrowerFile's/InternalDocument's own
     * comments on this same pitfall: Hibernate 6 maps @Lob on a String to Postgres's oid
     * (large object) type by default, which doesn't match the actual TEXT column created
     * by the migration, causing a schema-validation failure at boot ("expecting oid but
     * found text"). A plain String with columnDefinition="TEXT" matches correctly.
     */
    @Column(columnDefinition = "TEXT")
    private String rawResponse;

    /*
     * Reviewed by compliance officer
     */
    private String reviewedBy;

    /*
     * Manual decision
     */
    @Enumerated(EnumType.STRING)
    private ManualDecision manualDecision;

    /*
     * When reviewed
     */
    private LocalDateTime reviewedAt;

    /*
     * Expiry
     */
    private LocalDateTime expiresAt;

    /*
     * Audit
     */
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {

        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();

        if (result == null)
            result = CheckResult.PENDING;

        if (riskLevel == null)
            riskLevel = RiskLevel.UNKNOWN;

        if (expiresAt == null)
            expiresAt = createdAt.plusYears(1);
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return expiresAt != null &&
                expiresAt.isBefore(LocalDateTime.now());
    }

    /*
     * Types
     */

    public enum CheckType {

        IDENTITY_VERIFICATION,

        DOCUMENT_VERIFICATION,

        FACE_MATCH,

        LIVENESS_CHECK,

        ADDRESS_VERIFICATION,

        SANCTIONS_SCREENING,

        PEP_SCREENING,

        ADVERSE_MEDIA,

        AML_SCREENING,

        PHONE_VERIFICATION,

        EMAIL_VERIFICATION,

        TAX_VERIFICATION
    }

    public enum CheckResult {

    PENDING,
    CLEAR,
    FLAGGED,
    REJECTED,
    MANUAL_REVIEW
    }

    public enum RiskLevel {

        LOW,

        MEDIUM,

        HIGH,

        CRITICAL,

        UNKNOWN
    }

    public enum ManualDecision {

        APPROVED,

        REJECTED,

        ESCALATED,

        NONE
    }

}
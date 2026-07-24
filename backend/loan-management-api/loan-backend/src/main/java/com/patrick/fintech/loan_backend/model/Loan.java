package com.patrick.fintech.loan_backend.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Entity
@Table(name = "loans",
    indexes = {
        @Index(name = "idx_loans_org", columnList = "organization_id"),
        @Index(name = "idx_loans_borrower", columnList = "borrower_id"),
        @Index(name = "idx_loans_status", columnList = "status")
    })
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Loan {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String referenceNumber;  // e.g. KCB-2024-000123

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "borrower_id", nullable = false)
    private Borrower borrower;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_officer_id")
    private User loanOfficer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoanType loanType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoanStatus status;

    @Enumerated(EnumType.STRING)
    private RepaymentFrequency repaymentFrequency;

    private Double  amount;
    private Double  interestRate;        // meaning depends on interestRateType — see LoanProduct
    @Builder.Default
    private String  interestRateType = "ANNUAL"; // MONTHLY or ANNUAL — copied from the product at creation time
    private Integer durationMonths;
    private String  currency;            // ISO-4217
    private Double  processingFeeRate;   // % of principal
    private Double  processingFee;
    private Double  disbursedAmount;
    private Double  totalRepayable;
    private Double  totalPaid;
    private Double  outstandingBalance;

    private String  notes;
    private String  purpose;
    private String  collateralDescription;
    private Double  collateralValue;
    private String  rejectionReason;
    private String  internalNotes;

    // Risk
    private Double  riskScore;
    private String  riskCategory;        // LOW, MEDIUM, HIGH, CRITICAL
    private Double  debtToIncomeRatio;
    private Integer creditScoreSnapshot;

    private LocalDate startDate;
    private LocalDate approvedAt;
    private LocalDate disbursedAt;
    private LocalDate maturityDate;
    private LocalDate nextDueDate;
    private LocalDate lastPaymentDate;

    private Integer missedInstallments;
    private Integer daysOverdue;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @JsonIgnore
    @OneToMany(mappedBy = "loan", cascade = CascadeType.ALL)
    private List<Payment> payments;

    @PrePersist protected void onCreate() {
        createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now();
        if (status == null) status = LoanStatus.PENDING;
        if (interestRateType == null) interestRateType = "ANNUAL";
        if (missedInstallments == null) missedInstallments = 0;
        if (daysOverdue == null) daysOverdue = 0;
        if (totalPaid == null) totalPaid = 0.0;
        if (processingFeeRate == null) processingFeeRate = 2.0;
    }

    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    public enum LoanType {
        PERSONAL, MORTGAGE, AUTO, BUSINESS, STUDENT, EMERGENCY,
        ASSET_FINANCE, SALARY_ADVANCE, MICROFINANCE, AGRICULTURAL, TRADE_FINANCE, GROUP
    }

    public enum RepaymentFrequency { WEEKLY, BIWEEKLY, MONTHLY, QUARTERLY, BULLET }
}

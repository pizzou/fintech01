package com.patrick.fintech.loan_backend.dto.regulatory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BnrSummaryReport {

    // ============================================================
    // 1. INSTITUTION
    // ============================================================

    private Long organizationId;

    private String organizationName;

    private String bnrInstitutionCode;

    private String registrationNumber;

    private String institutionType;

    private String country;

    private String currency;

    // ============================================================
    // 2. REPORT
    // ============================================================

    private String reportPeriod;

    private LocalDate periodStart;

    private LocalDate periodEnd;

    private LocalDate reportDate;

    private LocalDateTime generatedAt;

    private String generatedBy;

    private String reportReference;

    // ============================================================
    // 3. BRANCH
    // ============================================================

    private Long branchId;

    private String branchName;

    private long totalBranches;

    // ============================================================
    // 4. LOAN COUNTS
    // ============================================================

    private long totalLoans;

    private long loansDisbursedDuringPeriod;

    private long activeLoans;

    private long closedLoans;

    private long paidLoans;

    private long pendingLoans;

    private long approvedLoans;

    private long rejectedLoans;

    private long cancelledLoans;

    private long overdueLoans;

    private long defaultedLoans;

    private long writtenOffLoans;

    private long restructuredLoans;

    // ============================================================
    // 5. DISBURSEMENTS
    // ============================================================

    private BigDecimal totalPrincipalDisbursed;

    private BigDecimal totalApprovedAmount;

    private BigDecimal averageLoanSize;

    private BigDecimal largestLoanAmount;

    private BigDecimal smallestLoanAmount;

    // ============================================================
    // 6. OUTSTANDING
    // ============================================================

    private BigDecimal outstandingPrincipal;

    private BigDecimal outstandingInterest;

    private BigDecimal outstandingFees;

    private BigDecimal totalOutstanding;

    // ============================================================
    // 7. REPAYMENTS
    // ============================================================

    private BigDecimal totalPrincipalCollected;

    private BigDecimal totalInterestCollected;

    private BigDecimal totalFeesCollected;

    private BigDecimal totalAmountCollected;

    private BigDecimal interestAccruedUnpaid;

    private BigDecimal feesAccruedUnpaid;

    private long totalPayments;

    private long missedPayments;

    private long overduePayments;

    // ============================================================
    // 8. PAR
    // ============================================================

    private BigDecimal parAmount;

    private BigDecimal parRatio;

    private BigDecimal par1To30Amount;

    private BigDecimal par31To60Amount;

    private BigDecimal par61To90Amount;

    private BigDecimal par91To180Amount;

    private BigDecimal par181To365Amount;

    private BigDecimal parOver365Amount;

    // ============================================================
    // 9. NPL
    // ============================================================

    private BigDecimal nplAmount;

    private BigDecimal nplRatio;

    private long nplLoanCount;

    private long loansOver30Days;

    private long loansOver60Days;

    private long loansOver90Days;

    private long loansOver180Days;

    private long loansOver365Days;

    // ============================================================
    // 10. DEFAULT / WRITE-OFF
    // ============================================================

    private BigDecimal defaultedAmount;

    private BigDecimal writtenOffAmount;

    private BigDecimal recoveriesAfterWriteOff;

    // ============================================================
    // 11. PROVISION
    // ============================================================

    private BigDecimal requiredProvision;

    private BigDecimal existingProvision;

    private BigDecimal provisionShortfall;

    // ============================================================
    // 12. BORROWERS
    // ============================================================

    private long totalBorrowers;

    private long activeBorrowers;

    private long maleBorrowers;

    private long femaleBorrowers;

    private long otherGenderBorrowers;

    private long borrowersWithMultipleLoans;

    // ============================================================
    // 13. DEMOGRAPHICS
    // ============================================================

    private long youthBorrowers;

    private long adultBorrowers;

    private long seniorBorrowers;

    // ============================================================
    // 14. CREDIT BUREAU
    // ============================================================

    private long borrowersCreditChecked;

    private long borrowersWithDefaultHistory;

    private long borrowersWithActiveListing;

    private long borrowersWithMultipleFacilities;

    private BigDecimal totalExternalDebt;

    // ============================================================
    // 15. BREAKDOWNS
    // ============================================================

    private List<BnrBreakdownRow> loanTypeBreakdown;

    private List<BnrBreakdownRow> branchBreakdown;

    private List<BnrBreakdownRow> genderBreakdown;

    // ============================================================
    // 16. DATA QUALITY
    // ============================================================

    private long loansMissingBorrower;

    private long borrowersMissingNationalId;

    private long loansMissingBranch;

    private long loansMissingCurrency;

    private long loansMissingRepaymentSchedule;

    private List<String> dataQualityWarnings;

    // ============================================================
    // 17. STATUS
    // ============================================================

    private String reportStatus;

    private String submissionReference;
}
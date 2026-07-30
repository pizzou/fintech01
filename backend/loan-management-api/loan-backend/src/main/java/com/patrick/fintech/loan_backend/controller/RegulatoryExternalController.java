package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.dto.regulatory.BnrBreakdownRow;
import com.patrick.fintech.loan_backend.dto.regulatory.BnrSummaryReport;
import com.patrick.fintech.loan_backend.dto.regulatory.CreditBureauRecord;
import com.patrick.fintech.loan_backend.repository.OrganizationRepository;
import com.patrick.fintech.loan_backend.security.RegulatoryApiPrincipal;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.service.RegulatoryReportingService;
import com.patrick.fintech.loan_backend.service.RegulatoryReportingService.ReportPeriod;
import com.patrick.fintech.loan_backend.service.ReportExportService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * External regulatory integration endpoints.
 *
 * These endpoints are intended for BNR and authorized credit-bureau
 * integrations authenticated through RegulatoryApiPrincipal / API keys.
 *
 * Organization scope is obtained exclusively from the authenticated API
 * principal. The organization ID is never accepted from the request.
 */
@RestController
@RequestMapping("/api/regulatory/external")
@RequiredArgsConstructor
public class RegulatoryExternalController {

    private final RegulatoryReportingService reportingService;
    private final ReportExportService exportService;
    private final AuditService auditService;
    private final OrganizationRepository organizationRepository;

    // ============================================================
    // SECURITY PRINCIPAL
    // ============================================================

    private RegulatoryApiPrincipal principal() {

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            throw new IllegalStateException(
                    "No authenticated regulatory API principal."
            );
        }

        Object principal =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
                        .getPrincipal();

        if (!(principal instanceof RegulatoryApiPrincipal)) {
            throw new IllegalStateException(
                    "Authenticated principal is not a RegulatoryApiPrincipal."
            );
        }

        return (RegulatoryApiPrincipal) principal;
    }

    // ============================================================
    // AUDIT
    // ============================================================

    private void audit(
            String action,
            String description
    ) {

        RegulatoryApiPrincipal p = principal();

        auditService.log(
                organizationRepository
                        .findById(p.getOrganizationId())
                        .orElse(null),

                null,

                action,

                "RegulatoryApiAccess",

                p.getClientName(),

                "[" +
                        p.getClientType() +
                        " API: " +
                        p.getClientName() +
                        "] " +
                        description,

                null,
                null,

                "Regulatory Reporting"
        );
    }

    // ============================================================
    // BNR SUMMARY
    // ============================================================

    @GetMapping("/bnr/summary")
    @PreAuthorize("hasAuthority('ROLE_BNR_API')")
    public ResponseEntity<ApiResponse<BnrSummaryReport>> bnrSummary(

            @RequestParam(
                    required = false,
                    defaultValue = "MONTHLY"
            )
            ReportPeriod period,

            @RequestParam(required = false)
            String from,

            @RequestParam(required = false)
            String to
    ) {

        Long orgId =
                principal().getOrganizationId();

        BnrSummaryReport report =
                reportingService.buildBnrSummary(
                        orgId,
                        null,
                        period,
                        parseDate(from),
                        parseDate(to)
                );

        audit(
                "VIEW",
                "Fetched BNR portfolio summary (" +
                        period +
                        ")"
        );

        return ResponseEntity.ok(
                ApiResponse.ok(report)
        );
    }

    // ============================================================
    // BNR - LOAN TYPE BREAKDOWN
    // ============================================================

    @GetMapping("/bnr/breakdown/loan-type")
    @PreAuthorize("hasAuthority('ROLE_BNR_API')")
    public ResponseEntity<ApiResponse<List<BnrBreakdownRow>>>
    bnrByLoanType(

            @RequestParam(
                    required = false,
                    defaultValue = "MONTHLY"
            )
            ReportPeriod period,

            @RequestParam(required = false)
            String from,

            @RequestParam(required = false)
            String to
    ) {

        Long orgId =
                principal().getOrganizationId();

        List<BnrBreakdownRow> rows =
                reportingService.breakdownByLoanType(
                        orgId,
                        null,
                        period,
                        parseDate(from),
                        parseDate(to)
                );

        audit(
                "VIEW",
                "Fetched BNR loan-type breakdown (" +
                        period +
                        ")"
        );

        return ResponseEntity.ok(
                ApiResponse.ok(rows)
        );
    }

    // ============================================================
    // BNR - BRANCH BREAKDOWN
    // ============================================================

    @GetMapping("/bnr/breakdown/branch")
    @PreAuthorize("hasAuthority('ROLE_BNR_API')")
    public ResponseEntity<ApiResponse<List<BnrBreakdownRow>>>
    bnrByBranch(

            @RequestParam(
                    required = false,
                    defaultValue = "MONTHLY"
            )
            ReportPeriod period,

            @RequestParam(required = false)
            String from,

            @RequestParam(required = false)
            String to
    ) {

        Long orgId =
                principal().getOrganizationId();

        List<BnrBreakdownRow> rows =
                reportingService.breakdownByBranch(
                        orgId,
                        period,
                        parseDate(from),
                        parseDate(to)
                );

        audit(
                "VIEW",
                "Fetched BNR branch breakdown (" +
                        period +
                        ")"
        );

        return ResponseEntity.ok(
                ApiResponse.ok(rows)
        );
    }

    // ============================================================
    // BNR - GENDER BREAKDOWN
    // ============================================================

    @GetMapping("/bnr/breakdown/gender")
    @PreAuthorize("hasAuthority('ROLE_BNR_API')")
    public ResponseEntity<ApiResponse<List<BnrBreakdownRow>>>
    bnrByGender(

            @RequestParam(
                    required = false,
                    defaultValue = "MONTHLY"
            )
            ReportPeriod period,

            @RequestParam(required = false)
            String from,

            @RequestParam(required = false)
            String to
    ) {

        Long orgId =
                principal().getOrganizationId();

        List<BnrBreakdownRow> rows =
                reportingService.breakdownByGender(
                        orgId,
                        null,
                        period,
                        parseDate(from),
                        parseDate(to)
                );

        audit(
                "VIEW",
                "Fetched BNR gender breakdown (" +
                        period +
                        ")"
        );

        return ResponseEntity.ok(
                ApiResponse.ok(rows)
        );
    }

    // ============================================================
    // BNR EXPORT
    // ============================================================

    @GetMapping(
            value = "/bnr/export",
            produces = {
                    MediaType.APPLICATION_JSON_VALUE,
                    "text/csv",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    MediaType.APPLICATION_PDF_VALUE
            }
    )
    @PreAuthorize("hasAuthority('ROLE_BNR_API')")
    public ResponseEntity<?> bnrExport(

            @RequestParam(
                    defaultValue = "json"
            )
            String format,

            @RequestParam(
                    required = false,
                    defaultValue = "MONTHLY"
            )
            ReportPeriod period,

            @RequestParam(required = false)
            String from,

            @RequestParam(required = false)
            String to
    ) {

        Long orgId =
                principal().getOrganizationId();

        BnrSummaryReport summary =
                reportingService.buildBnrSummary(
                        orgId,
                        null,
                        period,
                        parseDate(from),
                        parseDate(to)
                );

        audit(
                "EXPORT",
                "Exported BNR portfolio summary as " +
                        format.toUpperCase() +
                        " (" +
                        period +
                        ")"
        );

        // --------------------------------------------------------
        // JSON
        // --------------------------------------------------------

        if ("json".equalsIgnoreCase(format)) {

            return ResponseEntity.ok(
                    ApiResponse.ok(summary)
            );
        }

        String orgName =
                organizationRepository
                        .findById(orgId)
                        .map(o -> o.getName())
                        .orElse("");

        List<String> columns =
                List.of(
                        "Metric",
                        "Value"
                );

        List<Map<String, Object>> rows =
                flattenSummary(summary);

        return fileResponse(
                format,
                "bnr-summary",
                "BNR Loan Portfolio Summary",
                columns,
                rows,
                orgName
        );
    }

    // ============================================================
    // CREDIT BUREAU EXPORT
    // ============================================================

    @GetMapping(
            value = "/credit-bureau/export",
            produces = {
                    MediaType.APPLICATION_JSON_VALUE,
                    "text/csv",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    MediaType.APPLICATION_PDF_VALUE
            }
    )
    @PreAuthorize("hasAuthority('ROLE_CREDIT_BUREAU_API')")
    public ResponseEntity<?> creditBureauExport(

            @RequestParam(
                    defaultValue = "json"
            )
            String format,

            @RequestParam(required = false)
            String from,

            @RequestParam(required = false)
            String to
    ) {

        Long orgId =
                principal().getOrganizationId();

        List<CreditBureauRecord> records =
                reportingService.buildCreditBureauExport(
                        orgId,
                        null,
                        parseDate(from),
                        parseDate(to)
                );

        audit(
                "EXPORT",
                "Exported " +
                        records.size() +
                        " borrower credit records as " +
                        format.toUpperCase()
        );

        // --------------------------------------------------------
        // JSON
        // --------------------------------------------------------

        if ("json".equalsIgnoreCase(format)) {

            return ResponseEntity.ok(
                    ApiResponse.ok(records)
            );
        }

        String orgName =
                organizationRepository
                        .findById(orgId)
                        .map(o -> o.getName())
                        .orElse("");

        List<String> columns =
                List.of(
                        "National ID",
                        "Full Name",
                        "Date of Birth",
                        "Gender",
                        "Phone",
                        "Loan Number",
                        "Loan Type",
                        "Loan Amount",
                        "Outstanding Balance",
                        "Status",
                        "Days Past Due",
                        "Credit Score",
                        "Date Opened",
                        "Last Payment",
                        "Date Closed",
                        "Branch",
                        "Currency"
                );

        List<Map<String, Object>> rows =
                records.stream()
                        .map(r -> {

                            Map<String, Object> m =
                                    new LinkedHashMap<>();

                            m.put(
                                    "National ID",
                                    r.getNationalId()
                            );

                            m.put(
                                    "Full Name",
                                    r.getFullName()
                            );

                            m.put(
                                    "Date of Birth",
                                    r.getDateOfBirth()
                            );

                            m.put(
                                    "Gender",
                                    r.getGender()
                            );

                            m.put(
                                    "Phone",
                                    r.getPhone()
                            );

                            m.put(
                                    "Loan Number",
                                    r.getLoanNumber()
                            );

                            m.put(
                                    "Loan Type",
                                    r.getLoanType()
                            );

                            m.put(
                                    "Loan Amount",
                                    r.getLoanAmount()
                            );

                            m.put(
                                    "Outstanding Balance",
                                    r.getOutstandingBalance()
                            );

                            m.put(
                                    "Status",
                                    r.getLoanStatus()
                            );

                            m.put(
                                    "Days Past Due",
                                    r.getDaysPastDue()
                            );

                            m.put(
                                    "Credit Score",
                                    r.getCreditScore()
                            );

                            m.put(
                                    "Date Opened",
                                    r.getDateOpened()
                            );

                            m.put(
                                    "Last Payment",
                                    r.getLastPaymentDate()
                            );

                            m.put(
                                    "Date Closed",
                                    r.getDateClosed()
                            );

                            m.put(
                                    "Branch",
                                    r.getBranchName()
                            );

                            m.put(
                                    "Currency",
                                    r.getCurrency()
                            );

                            return m;
                        })
                        .toList();

        return fileResponse(
                format,
                "credit-bureau-export",
                "Credit Bureau Export",
                columns,
                rows,
                orgName
        );
    }

    // ============================================================
    // FLATTEN BNR SUMMARY
    // ============================================================

    private List<Map<String, Object>> flattenSummary(
            BnrSummaryReport s
    ) {

        List<Map<String, Object>> rows =
                new ArrayList<>();

        java.util.function.BiConsumer<String, Object> add =
                (key, value) -> {

                    Map<String, Object> row =
                            new LinkedHashMap<>();

                    row.put(
                            "Metric",
                            key
                    );

                    row.put(
                            "Value",
                            value
                    );

                    rows.add(row);
                };

        String currency =
                s.getCurrency() == null
                        ? ""
                        : s.getCurrency();

        // --------------------------------------------------------
        // REPORT INFORMATION
        // --------------------------------------------------------

        add.accept(
                "Report Period",
                safe(s.getReportPeriod()) +
                        " (" +
                        s.getPeriodStart() +
                        " to " +
                        s.getPeriodEnd() +
                        ")"
        );

        add.accept(
                "Institution",
                safe(s.getOrganizationName()) +
                        (
                                s.getBnrInstitutionCode() != null
                                        ? " (" +
                                        s.getBnrInstitutionCode() +
                                        ")"
                                        : ""
                        )
        );

        add.accept(
                "Institution Type",
                s.getInstitutionType()
        );

        add.accept(
                "Registration Number",
                s.getRegistrationNumber()
        );

        add.accept(
                "Branch",
                s.getBranchName()
        );

        // --------------------------------------------------------
        // LOAN COUNTS
        // --------------------------------------------------------

        add.accept(
                "Total Loans",
                s.getTotalLoans()
        );

        add.accept(
                "Loans Disbursed During Period",
                s.getLoansDisbursedDuringPeriod()
        );

        add.accept(
                "Active Loans",
                s.getActiveLoans()
        );

        add.accept(
                "Closed Loans",
                s.getClosedLoans()
        );

        add.accept(
                "Paid Loans",
                s.getPaidLoans()
        );

        add.accept(
                "Pending Loans",
                s.getPendingLoans()
        );

        add.accept(
                "Approved Loans",
                s.getApprovedLoans()
        );

        add.accept(
                "Rejected Loans",
                s.getRejectedLoans()
        );

        add.accept(
                "Cancelled Loans",
                s.getCancelledLoans()
        );

        add.accept(
                "Overdue Loans",
                s.getOverdueLoans()
        );

        add.accept(
                "Defaulted Loans",
                s.getDefaultedLoans()
        );

        add.accept(
                "Written-off Loans",
                s.getWrittenOffLoans()
        );

        add.accept(
                "Restructured Loans",
                s.getRestructuredLoans()
        );

        // --------------------------------------------------------
        // DISBURSEMENTS
        // --------------------------------------------------------

        add.accept(
                "Total Principal Disbursed (" +
                        currency +
                        ")",
                s.getTotalPrincipalDisbursed()
        );

        add.accept(
                "Total Approved Amount (" +
                        currency +
                        ")",
                s.getTotalApprovedAmount()
        );

        add.accept(
                "Average Loan Size (" +
                        currency +
                        ")",
                s.getAverageLoanSize()
        );

        add.accept(
                "Largest Loan Amount (" +
                        currency +
                        ")",
                s.getLargestLoanAmount()
        );

        add.accept(
                "Smallest Loan Amount (" +
                        currency +
                        ")",
                s.getSmallestLoanAmount()
        );

        // --------------------------------------------------------
        // OUTSTANDING
        // --------------------------------------------------------

        add.accept(
                "Outstanding Principal (" +
                        currency +
                        ")",
                s.getOutstandingPrincipal()
        );

        add.accept(
                "Outstanding Interest (" +
                        currency +
                        ")",
                s.getOutstandingInterest()
        );

        add.accept(
                "Outstanding Fees (" +
                        currency +
                        ")",
                s.getOutstandingFees()
        );

        add.accept(
                "Total Outstanding (" +
                        currency +
                        ")",
                s.getTotalOutstanding()
        );

        // --------------------------------------------------------
        // REPAYMENTS
        // --------------------------------------------------------

        add.accept(
                "Total Principal Collected (" +
                        currency +
                        ")",
                s.getTotalPrincipalCollected()
        );

        add.accept(
                "Total Interest Collected (" +
                        currency +
                        ")",
                s.getTotalInterestCollected()
        );

        add.accept(
                "Total Fees Collected (" +
                        currency +
                        ")",
                s.getTotalFeesCollected()
        );

        add.accept(
                "Total Amount Collected (" +
                        currency +
                        ")",
                s.getTotalAmountCollected()
        );

        add.accept(
                "Interest Accrued but Unpaid (" +
                        currency +
                        ")",
                s.getInterestAccruedUnpaid()
        );

        add.accept(
                "Fees Accrued but Unpaid (" +
                        currency +
                        ")",
                s.getFeesAccruedUnpaid()
        );

        add.accept(
                "Total Payments",
                s.getTotalPayments()
        );

        add.accept(
                "Missed Payments",
                s.getMissedPayments()
        );

        add.accept(
                "Overdue Payments",
                s.getOverduePayments()
        );

        // --------------------------------------------------------
        // PAR
        // --------------------------------------------------------

        add.accept(
                "PAR Amount (" +
                        currency +
                        ")",
                s.getParAmount()
        );

        add.accept(
                "PAR Ratio",
                percent(s.getParRatio())
        );

        add.accept(
                "PAR 1-30 Days (" +
                        currency +
                        ")",
                s.getPar1To30Amount()
        );

        add.accept(
                "PAR 31-60 Days (" +
                        currency +
                        ")",
                s.getPar31To60Amount()
        );

        add.accept(
                "PAR 61-90 Days (" +
                        currency +
                        ")",
                s.getPar61To90Amount()
        );

        add.accept(
                "PAR 91-180 Days (" +
                        currency +
                        ")",
                s.getPar91To180Amount()
        );

        add.accept(
                "PAR 181-365 Days (" +
                        currency +
                        ")",
                s.getPar181To365Amount()
        );

        add.accept(
                "PAR Over 365 Days (" +
                        currency +
                        ")",
                s.getParOver365Amount()
        );

        add.accept(
                "Loans Over 30 Days",
                s.getLoansOver30Days()
        );

        add.accept(
                "Loans Over 60 Days",
                s.getLoansOver60Days()
        );

        add.accept(
                "Loans Over 90 Days",
                s.getLoansOver90Days()
        );

        add.accept(
                "Loans Over 180 Days",
                s.getLoansOver180Days()
        );

        add.accept(
                "Loans Over 365 Days",
                s.getLoansOver365Days()
        );

        // --------------------------------------------------------
        // NPL
        // --------------------------------------------------------

        add.accept(
                "NPL Amount (" +
                        currency +
                        ")",
                s.getNplAmount()
        );

        add.accept(
                "NPL Ratio",
                percent(s.getNplRatio())
        );

        add.accept(
                "NPL Loan Count",
                s.getNplLoanCount()
        );

        // --------------------------------------------------------
        // DEFAULT / WRITE-OFF
        // --------------------------------------------------------

        add.accept(
                "Defaulted Amount (" +
                        currency +
                        ")",
                s.getDefaultedAmount()
        );

        add.accept(
                "Written-off Amount (" +
                        currency +
                        ")",
                s.getWrittenOffAmount()
        );

        add.accept(
                "Recoveries After Write-off (" +
                        currency +
                        ")",
                s.getRecoveriesAfterWriteOff()
        );

        // --------------------------------------------------------
        // PROVISION
        // --------------------------------------------------------

        add.accept(
                "Required Provision (" +
                        currency +
                        ")",
                s.getRequiredProvision()
        );

        add.accept(
                "Existing Provision (" +
                        currency +
                        ")",
                s.getExistingProvision()
        );

        add.accept(
                "Provision Shortfall (" +
                        currency +
                        ")",
                s.getProvisionShortfall()
        );

        // --------------------------------------------------------
        // BORROWERS
        // --------------------------------------------------------

        add.accept(
                "Total Borrowers",
                s.getTotalBorrowers()
        );

        add.accept(
                "Active Borrowers",
                s.getActiveBorrowers()
        );

        add.accept(
                "Male Borrowers",
                s.getMaleBorrowers()
        );

        add.accept(
                "Female Borrowers",
                s.getFemaleBorrowers()
        );

        add.accept(
                "Other Gender Borrowers",
                s.getOtherGenderBorrowers()
        );

        add.accept(
                "Borrowers With Multiple Loans",
                s.getBorrowersWithMultipleLoans()
        );

        // --------------------------------------------------------
        // FINANCIAL INCLUSION
        // --------------------------------------------------------

        add.accept(
                "Youth Borrowers",
                s.getYouthBorrowers()
        );

        add.accept(
                "Adult Borrowers",
                s.getAdultBorrowers()
        );

        add.accept(
                "Senior Borrowers",
                s.getSeniorBorrowers()
        );

        // --------------------------------------------------------
        // CREDIT INFORMATION
        // --------------------------------------------------------

        add.accept(
                "Borrowers Credit Checked",
                s.getBorrowersCreditChecked()
        );

        add.accept(
                "Borrowers With Default History",
                s.getBorrowersWithDefaultHistory()
        );

        add.accept(
                "Borrowers With Active Listing",
                s.getBorrowersWithActiveListing()
        );

        add.accept(
                "Borrowers With Multiple Facilities",
                s.getBorrowersWithMultipleFacilities()
        );

        add.accept(
                "Total External Debt (" +
                        currency +
                        ")",
                s.getTotalExternalDebt()
        );

        // --------------------------------------------------------
        // DATA QUALITY
        // --------------------------------------------------------

        add.accept(
                "Loans Missing Borrower",
                s.getLoansMissingBorrower()
        );

        add.accept(
                "Borrowers Missing National ID",
                s.getBorrowersMissingNationalId()
        );

        add.accept(
                "Loans Missing Branch",
                s.getLoansMissingBranch()
        );

        add.accept(
                "Loans Missing Currency",
                s.getLoansMissingCurrency()
        );

        add.accept(
                "Loans Missing Repayment Schedule",
                s.getLoansMissingRepaymentSchedule()
        );

        add.accept(
                "Report Status",
                s.getReportStatus()
        );

        return rows;
    }

    // ============================================================
    // FILE RESPONSE
    // ============================================================

    private ResponseEntity<byte[]> fileResponse(

            String format,

            String filenameBase,

            String title,

            List<String> columns,

            List<Map<String, Object>> rows,

            String orgName
    ) {

        if (format == null ||
                format.isBlank()) {

            format = "xlsx";
        }

        byte[] bytes;
        MediaType contentType;
        String extension;

        switch (format.toLowerCase()) {

            case "csv" -> {

                bytes =
                        BnrReportController.toCsv(
                                columns,
                                rows
                        );

                contentType =
                        MediaType.parseMediaType(
                                "text/csv"
                        );

                extension = "csv";
            }

            case "pdf" -> {

                bytes =
                        exportService.toPdf(
                                title,
                                columns,
                                rows,
                                orgName
                        );

                contentType =
                        MediaType.APPLICATION_PDF;

                extension = "pdf";
            }

            case "xlsx" -> {

                bytes =
                        exportService.toExcel(
                                title,
                                columns,
                                rows
                        );

                contentType =
                        MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        );

                extension = "xlsx";
            }

            default -> {

                throw new IllegalArgumentException(
                        "Unsupported export format: " +
                                format +
                                ". Supported formats: json, csv, pdf, xlsx."
                );
            }
        }

        return ResponseEntity.ok()

                .contentType(contentType)

                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" +
                                filenameBase +
                                "." +
                                extension +
                                "\""
                )

                .body(bytes);
    }

    // ============================================================
    // DATE
    // ============================================================

    private LocalDate parseDate(String value) {

        if (value == null ||
                value.isBlank()) {

            return null;
        }

        return LocalDate.parse(value);
    }

    // ============================================================
    // SAFE STRING
    // ============================================================

    private String safe(String value) {

        return value == null
                ? ""
                : value;
    }

    // ============================================================
    // PERCENT
    // ============================================================

    private String percent(Number value) {

        if (value == null) {
            return "0.00%";
        }

        return String.format(
                "%.2f%%",
                value.doubleValue() * 100.0
        );
    }
}
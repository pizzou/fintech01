package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.dto.regulatory.BnrBreakdownRow;
import com.patrick.fintech.loan_backend.dto.regulatory.BnrSummaryReport;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.service.RegulatoryReportingService;
import com.patrick.fintech.loan_backend.service.RegulatoryReportingService.ReportPeriod;
import com.patrick.fintech.loan_backend.service.ReportExportService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Staff-facing BNR regulatory report screens:
 *
 * - Portfolio summary
 * - Loan-type breakdown
 * - Branch breakdown
 * - Gender breakdown
 * - PDF export
 * - Excel export
 * - CSV export
 *
 * Authentication:
 * JWT / normal application authentication.
 *
 * Roles:
 * ADMIN, MANAGER, AUDITOR
 *
 * External regulatory access is handled separately by
 * RegulatoryExternalController.
 */
@RestController
@RequestMapping("/api/regulatory/bnr")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','AUDITOR')")
public class BnrReportController {

    private final RegulatoryReportingService reportingService;
    private final ReportExportService exportService;
    private final AuditService auditService;
    private final CurrentUserUtil currentUserUtil;

    // ============================================================
    // SUMMARY
    // ============================================================

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<BnrSummaryReport>> summary(

            @RequestParam(required = false)
            Long branchId,

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
                currentUserUtil.getCurrentOrganizationId();

        LocalDate parsedFrom =
                parseDate(from);

        LocalDate parsedTo =
                parseDate(to);

        BnrSummaryReport report =
                reportingService.buildBnrSummary(
                        orgId,
                        branchId,
                        period,
                        parsedFrom,
                        parsedTo
                );

        auditService.log(
                currentUserUtil.getCurrentUser().getOrganization(),
                currentUserUtil.getCurrentUser(),
                "VIEW",
                "BnrReport",
                period.name(),
                "Viewed BNR portfolio summary (" + period + ")",
                null,
                null,
                "Regulatory Reporting"
        );

        return ResponseEntity.ok(
                ApiResponse.ok(report)
        );
    }

    // ============================================================
    // BREAKDOWN - LOAN TYPE
    // ============================================================

    @GetMapping("/breakdown/loan-type")
    public ResponseEntity<ApiResponse<List<BnrBreakdownRow>>> byLoanType(

            @RequestParam(required = false)
            Long branchId,

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
                currentUserUtil.getCurrentOrganizationId();

        List<BnrBreakdownRow> result =
                reportingService.breakdownByLoanType(
                        orgId,
                        branchId,
                        period,
                        parseDate(from),
                        parseDate(to)
                );

        return ResponseEntity.ok(
                ApiResponse.ok(result)
        );
    }

    // ============================================================
    // BREAKDOWN - BRANCH
    // ============================================================

    @GetMapping("/breakdown/branch")
    public ResponseEntity<ApiResponse<List<BnrBreakdownRow>>> byBranch(

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
                currentUserUtil.getCurrentOrganizationId();

        List<BnrBreakdownRow> result =
                reportingService.breakdownByBranch(
                        orgId,
                        period,
                        parseDate(from),
                        parseDate(to)
                );

        return ResponseEntity.ok(
                ApiResponse.ok(result)
        );
    }

    // ============================================================
    // BREAKDOWN - GENDER
    // ============================================================

    @GetMapping("/breakdown/gender")
    public ResponseEntity<ApiResponse<List<BnrBreakdownRow>>> byGender(

            @RequestParam(required = false)
            Long branchId,

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
                currentUserUtil.getCurrentOrganizationId();

        List<BnrBreakdownRow> result =
                reportingService.breakdownByGender(
                        orgId,
                        branchId,
                        period,
                        parseDate(from),
                        parseDate(to)
                );

        return ResponseEntity.ok(
                ApiResponse.ok(result)
        );
    }

    // ============================================================
    // EXPORT
    // ============================================================

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(

            @RequestParam(
                    defaultValue = "xlsx"
            )
            String format,

            @RequestParam(required = false)
            Long branchId,

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
                currentUserUtil.getCurrentOrganizationId();

        BnrSummaryReport summary =
                reportingService.buildBnrSummary(
                        orgId,
                        branchId,
                        period,
                        parseDate(from),
                        parseDate(to)
                );

        String orgName =
                currentUserUtil
                        .getCurrentUser()
                        .getOrganization()
                        .getName();

        List<String> columns =
                List.of(
                        "Metric",
                        "Value"
                );

        List<Map<String, Object>> rows =
                summaryToRows(summary);

        String filename =
                "BNR-Portfolio-Summary-"
                        + LocalDate.now()
                        .format(DateTimeFormatter.ISO_DATE);

        auditService.log(
                currentUserUtil.getCurrentUser().getOrganization(),
                currentUserUtil.getCurrentUser(),
                "EXPORT",
                "BnrReport",
                period.name(),
                "Exported BNR portfolio summary as "
                        + format.toUpperCase(),
                null,
                null,
                "Regulatory Reporting"
        );

        return respond(
                format,
                filename,
                "BNR Loan Portfolio Summary",
                columns,
                rows,
                orgName
        );
    }

    // ============================================================
    // DATE PARSER
    // ============================================================

    private LocalDate parseDate(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        return LocalDate.parse(value);
    }

    // ============================================================
    // SUMMARY -> EXPORT ROWS
    // ============================================================

    private List<Map<String, Object>> summaryToRows(
            BnrSummaryReport s
    ) {

        List<Map<String, Object>> rows =
                new ArrayList<>();

        BiConsumer<String, Object> add =
                (metric, value) -> {

                    Map<String, Object> row =
                            new LinkedHashMap<>();

                    row.put("Metric", metric);
                    row.put("Value", value);

                    rows.add(row);
                };

        // ========================================================
        // REPORT INFORMATION
        // ========================================================

        add.accept(
                "Report Period",
                s.getReportPeriod()
                        + " ("
                        + s.getPeriodStart()
                        + " to "
                        + s.getPeriodEnd()
                        + ")"
        );

        add.accept(
                "Institution",
                s.getOrganizationName()
                        + (
                        s.getBnrInstitutionCode() != null
                                ? " (" + s.getBnrInstitutionCode() + ")"
                                : ""
                )
        );

        add.accept(
                "Registration Number",
                s.getRegistrationNumber()
        );

        add.accept(
                "Institution Type",
                s.getInstitutionType()
        );

        add.accept(
                "Country",
                s.getCountry()
        );

        add.accept(
                "Currency",
                s.getCurrency()
        );

        add.accept(
                "Report Date",
                s.getReportDate()
        );

        add.accept(
                "Generated At",
                s.getGeneratedAt()
        );

        add.accept(
                "Generated By",
                s.getGeneratedBy()
        );

        add.accept(
                "Report Reference",
                s.getReportReference()
        );

        // ========================================================
        // BRANCH
        // ========================================================

        add.accept(
                "Branch",
                s.getBranchName() != null
                        ? s.getBranchName()
                        : "ALL BRANCHES"
        );

        add.accept(
                "Total Branches",
                s.getTotalBranches()
        );

        // ========================================================
        // LOAN COUNTS
        // ========================================================

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

        // ========================================================
        // DISBURSEMENTS
        // ========================================================

        add.accept(
                "Total Principal Disbursed (" + s.getCurrency() + ")",
                s.getTotalPrincipalDisbursed()
        );

        add.accept(
                "Total Approved Amount (" + s.getCurrency() + ")",
                s.getTotalApprovedAmount()
        );

        add.accept(
                "Average Loan Size (" + s.getCurrency() + ")",
                s.getAverageLoanSize()
        );

        add.accept(
                "Largest Loan Amount (" + s.getCurrency() + ")",
                s.getLargestLoanAmount()
        );

        add.accept(
                "Smallest Loan Amount (" + s.getCurrency() + ")",
                s.getSmallestLoanAmount()
        );

        // ========================================================
        // OUTSTANDING
        // ========================================================

        add.accept(
                "Outstanding Principal (" + s.getCurrency() + ")",
                s.getOutstandingPrincipal()
        );

        add.accept(
                "Outstanding Interest (" + s.getCurrency() + ")",
                s.getOutstandingInterest()
        );

        add.accept(
                "Outstanding Fees (" + s.getCurrency() + ")",
                s.getOutstandingFees()
        );

        add.accept(
                "Total Outstanding (" + s.getCurrency() + ")",
                s.getTotalOutstanding()
        );

        // ========================================================
        // REPAYMENTS
        // ========================================================

        add.accept(
                "Total Principal Collected (" + s.getCurrency() + ")",
                s.getTotalPrincipalCollected()
        );

        add.accept(
                "Total Interest Collected (" + s.getCurrency() + ")",
                s.getTotalInterestCollected()
        );

        add.accept(
                "Total Fees Collected (" + s.getCurrency() + ")",
                s.getTotalFeesCollected()
        );

        add.accept(
                "Total Amount Collected (" + s.getCurrency() + ")",
                s.getTotalAmountCollected()
        );

        add.accept(
                "Interest Accrued but Unpaid (" + s.getCurrency() + ")",
                s.getInterestAccruedUnpaid()
        );

        add.accept(
                "Fees Accrued but Unpaid (" + s.getCurrency() + ")",
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

        // ========================================================
        // PAR
        // ========================================================

        add.accept(
                "Portfolio at Risk (PAR) Amount (" + s.getCurrency() + ")",
                s.getParAmount()
        );

        add.accept(
                "PAR Ratio",
                percentage(s.getParRatio())
        );

        add.accept(
                "PAR 1-30 Days (" + s.getCurrency() + ")",
                s.getPar1To30Amount()
        );

        add.accept(
                "PAR 31-60 Days (" + s.getCurrency() + ")",
                s.getPar31To60Amount()
        );

        add.accept(
                "PAR 61-90 Days (" + s.getCurrency() + ")",
                s.getPar61To90Amount()
        );

        add.accept(
                "PAR 91-180 Days (" + s.getCurrency() + ")",
                s.getPar91To180Amount()
        );

        add.accept(
                "PAR 181-365 Days (" + s.getCurrency() + ")",
                s.getPar181To365Amount()
        );

        add.accept(
                "PAR Over 365 Days (" + s.getCurrency() + ")",
                s.getParOver365Amount()
        );

        // ========================================================
        // NPL
        // ========================================================

        add.accept(
                "NPL Amount (>90 days) (" + s.getCurrency() + ")",
                s.getNplAmount()
        );

        add.accept(
                "NPL Ratio",
                percentage(s.getNplRatio())
        );

        add.accept(
                "NPL Loan Count",
                s.getNplLoanCount()
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

        // ========================================================
        // DEFAULT / WRITE-OFF
        // ========================================================

        add.accept(
                "Defaulted Amount (" + s.getCurrency() + ")",
                s.getDefaultedAmount()
        );

        add.accept(
                "Written-off Amount (" + s.getCurrency() + ")",
                s.getWrittenOffAmount()
        );

        add.accept(
                "Recoveries After Write-off (" + s.getCurrency() + ")",
                s.getRecoveriesAfterWriteOff()
        );

        // ========================================================
        // PROVISION
        // ========================================================

        add.accept(
                "Required Provision (" + s.getCurrency() + ")",
                s.getRequiredProvision()
        );

        add.accept(
                "Existing Provision (" + s.getCurrency() + ")",
                s.getExistingProvision()
        );

        add.accept(
                "Provision Shortfall (" + s.getCurrency() + ")",
                s.getProvisionShortfall()
        );

        // ========================================================
        // BORROWERS
        // ========================================================

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
                "Other / Unspecified Borrowers",
                s.getOtherGenderBorrowers()
        );

        add.accept(
                "Borrowers With Multiple Loans",
                s.getBorrowersWithMultipleLoans()
        );

        // ========================================================
        // FINANCIAL INCLUSION
        // ========================================================

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

        // ========================================================
        // CREDIT BUREAU
        // ========================================================

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
                "Total External Debt (" + s.getCurrency() + ")",
                s.getTotalExternalDebt()
        );

        // ========================================================
        // DATA QUALITY
        // ========================================================

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

        // ========================================================
        // REPORT STATUS
        // ========================================================

        add.accept(
                "Report Status",
                s.getReportStatus()
        );

        add.accept(
                "Submission Reference",
                s.getSubmissionReference()
        );

        return rows;
    }

    // ============================================================
    // PERCENTAGE
    // ============================================================

    private String percentage(Number ratio) {

        if (ratio == null) {
            return "0.00%";
        }

        return String.format(
                "%.2f%%",
                ratio.doubleValue() * 100
        );
    }

    // ============================================================
    // EXPORT RESPONSE
    // ============================================================

    private ResponseEntity<byte[]> respond(
            String format,
            String filename,
            String title,
            List<String> columns,
            List<Map<String, Object>> rows,
            String orgName
    ) {

        if (format == null || format.isBlank()) {
            format = "xlsx";
        }

        byte[] bytes;
        MediaType contentType;
        String extension;

        switch (format.toLowerCase()) {

            case "csv" -> {

                bytes =
                        toCsv(
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
                        "Unsupported export format: "
                                + format
                                + ". Supported formats: csv, pdf, xlsx."
                );
            }
        }

        return ResponseEntity.ok()
                .contentType(contentType)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\""
                                + filename
                                + "."
                                + extension
                                + "\""
                )
                .body(bytes);
    }

    // ============================================================
    // CSV
    // ============================================================

    static byte[] toCsv(
            List<String> columns,
            List<Map<String, Object>> rows
    ) {

        StringBuilder sb =
                new StringBuilder();

        sb.append(
                String.join(
                        ",",
                        columns
                )
        ).append("\n");

        for (Map<String, Object> row : rows) {

            for (int i = 0; i < columns.size(); i++) {

                Object value =
                        row.get(
                                columns.get(i)
                        );

                String cell =
                        value == null
                                ? ""
                                : value.toString();

                cell =
                        cell.replace(
                                "\"",
                                "\"\""
                        );

                if (
                        cell.contains(",") ||
                        cell.contains("\"") ||
                        cell.contains("\n") ||
                        cell.contains("\r")
                ) {

                    cell =
                            "\"" +
                            cell +
                            "\"";
                }

                sb.append(cell);

                if (i < columns.size() - 1) {
                    sb.append(",");
                }
            }

            sb.append("\n");
        }

        return sb.toString()
                .getBytes(
                        StandardCharsets.UTF_8
                );
    }
}
package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import com.patrick.fintech.loan_backend.model.Organization;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface LoanRepository extends JpaRepository<Loan, Long> {

    // ============================================================
    // GENERAL
    // ============================================================

    Optional<Loan> findByReferenceNumber(String referenceNumber);

    Optional<Loan> findByReferenceNumberAndBorrower_PhoneHash(
            String referenceNumber,
            String phoneHash
    );

    List<Loan> findByOrganization_Id(Long organizationId);

    List<Loan> findByBorrowerIdAndOrganizationId(
            Long borrowerId,
            Long organizationId
    );

    List<Loan> findByStatusIn(List<LoanStatus> statuses);

    List<Loan> findByBorrower_PhoneHash(String phoneHash);

    long countByOrganization(Organization organization);

    long countByOrganization_Id(Long organizationId);

    long countByOrganizationAndStatus(
            Organization organization,
            LoanStatus status
    );

    long countByOrganization_IdAndStatus(
            Long organizationId,
            LoanStatus status
    );

    // ============================================================
    // FILTERING
    // ============================================================

    @Query("""
        SELECT l
        FROM Loan l
        WHERE l.organization = :org
          AND (:status IS NULL OR l.status = :status)
          AND (:type IS NULL OR l.loanType = :type)
        ORDER BY l.createdAt DESC
        """)
    Page<Loan> findByFilters(
            @Param("org") Organization org,
            @Param("status") LoanStatus status,
            @Param("type") Loan.LoanType type,
            Pageable pageable
    );

    // ============================================================
    // DASHBOARD
    // ============================================================

    @Query("""
        SELECT COALESCE(SUM(l.amount), 0)
        FROM Loan l
        WHERE l.organization = :org
          AND l.status IN (
              'ACTIVE',
              'DISBURSED',
              'OVERDUE'
          )
        """)
    Double sumActivePrincipal(
            @Param("org") Organization org
    );

    @Query("""
        SELECT COALESCE(SUM(l.totalPaid), 0)
        FROM Loan l
        WHERE l.organization = :org
        """)
    Double sumTotalCollected(
            @Param("org") Organization org
    );

    @Query("""
        SELECT COALESCE(SUM(l.outstandingBalance), 0)
        FROM Loan l
        WHERE l.organization = :org
          AND l.status IN (
              'ACTIVE',
              'DISBURSED',
              'OVERDUE'
          )
        """)
    Double sumOutstandingBalance(
            @Param("org") Organization org
    );

    // ============================================================
    // LOAN TYPE BREAKDOWN
    // ============================================================

    @Query("""
        SELECT l.loanType,
               COUNT(l),
               COALESCE(SUM(l.amount), 0)
        FROM Loan l
        WHERE l.organization = :org
        GROUP BY l.loanType
        """)
    List<Object[]> getLoanTypeBreakdown(
            @Param("org") Organization org
    );

    // ============================================================
    // RECENT
    // ============================================================

    @Query("""
        SELECT l
        FROM Loan l
        WHERE l.organization = :org
        ORDER BY l.createdAt DESC
        """)
    List<Loan> findRecentByOrg(
            @Param("org") Organization org,
            Pageable pageable
    );

    // ============================================================
    // REGULATORY:
    // DISBURSEMENTS
    // ============================================================

    /**
     * Loans actually disbursed within the reporting period.
     *
     * Loan.disbursedAt is LocalDate, therefore LocalDate is used here.
     *
     * Period is inclusive:
     *
     * from <= disbursedAt <= to
     */
    @Query("""
        SELECT l
        FROM Loan l
        WHERE l.organization.id = :orgId
          AND (:branchId IS NULL OR l.branch.id = :branchId)
          AND l.disbursedAt IS NOT NULL
          AND l.disbursedAt >= :from
          AND l.disbursedAt <= :to
        ORDER BY l.disbursedAt ASC
        """)
    List<Loan> findLoansDisbursedDuringPeriod(
            @Param("orgId") Long orgId,
            @Param("branchId") Long branchId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    // ============================================================
    // REGULATORY:
    // PORTFOLIO AS OF DATE
    // ============================================================

    /**
     * Loans disbursed on or before the reporting date.
     *
     * WARNING:
     * This gives the correct population of loans existing by the
     * reporting date, but current Loan.status and current
     * daysOverdue values are still current-state values.
     *
     * For fully historical regulatory reporting, Loan needs a
     * daily/monthly snapshot table.
     */
    @EntityGraph(attributePaths = {
            "borrower",
            "branch",
            "payments"
    })
    @Query("""
        SELECT DISTINCT l
        FROM Loan l
        WHERE l.organization.id = :orgId
          AND (:branchId IS NULL OR l.branch.id = :branchId)
          AND l.disbursedAt IS NOT NULL
          AND l.disbursedAt <= :asOf
        ORDER BY l.disbursedAt ASC
        """)
    List<Loan> findPortfolioAsOf(
            @Param("orgId") Long orgId,
            @Param("branchId") Long branchId,
            @Param("asOf") LocalDate asOf
    );

    // ============================================================
    // OPERATIONAL REPORT
    // ============================================================

    @EntityGraph(attributePaths = {
            "borrower",
            "branch",
            "payments"
    })
    @Query("""
        SELECT DISTINCT l
        FROM Loan l
        WHERE l.organization.id = :orgId
          AND (:branchId IS NULL OR l.branch.id = :branchId)
          AND (:from IS NULL OR l.createdAt >= :from)
          AND (:to IS NULL OR l.createdAt < :to)
        ORDER BY l.createdAt DESC
        """)
    List<Loan> findForRegulatoryReport(
            @Param("orgId") Long orgId,
            @Param("branchId") Long branchId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    // ============================================================
    // LOANS CREATED
    // ============================================================

    @Query("""
        SELECT l
        FROM Loan l
        WHERE l.organization.id = :orgId
          AND (:branchId IS NULL OR l.branch.id = :branchId)
          AND l.createdAt >= :from
          AND l.createdAt < :to
        ORDER BY l.createdAt ASC
        """)
    List<Loan> findLoansCreatedDuringPeriod(
            @Param("orgId") Long orgId,
            @Param("branchId") Long branchId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}
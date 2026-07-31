package com.patrick.fintech.loan_backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patrick.fintech.loan_backend.config.CreditBureauProperties;
import com.patrick.fintech.loan_backend.dto.creditbureau.CreditBureauRequest;
import com.patrick.fintech.loan_backend.dto.creditbureau.CreditBureauResponse;
import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.CreditBureauCheck;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.repository.BorrowerRepository;
import com.patrick.fintech.loan_backend.repository.CreditBureauCheckRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.*;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.*;

import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreditBureauService {

    private final CreditBureauCheckRepository checkRepository;
    private final BorrowerRepository borrowerRepository;
    private final AuditService auditService;
    private final CreditBureauProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Run a credit bureau check.
     *
     * IMPORTANT:
     * In production, a real provider failure produces FAILED.
     * We do NOT convert provider failure into a fake credit score.
     */
    @Transactional
    public CreditBureauCheck runCheck(
            Long borrowerId,
            Long organizationId,
            String requestedBy
    ) {

        Borrower borrower = borrowerRepository
            .findById(borrowerId)
            .orElseThrow(() ->
                new IllegalArgumentException(
                    "Borrower not found: " + borrowerId
                )
            );

        if (borrower.getOrganization() == null ||
            borrower.getOrganization().getId() == null ||
            !borrower.getOrganization().getId().equals(organizationId)) {

            throw new SecurityException(
                "Borrower does not belong to this organization"
            );
        }

        CreditBureauCheck check = CreditBureauCheck.builder()
            .borrower(borrower)
            .organization(borrower.getOrganization())
            .reference(generateReference(borrower))
            .provider(properties.getProvider())
            .nationalIdChecked(borrower.getNationalId())
            .requestedBy(requestedBy)
            .status(CreditBureauCheck.CheckStatus.PROCESSING)
            .attemptCount(0)
            .build();

        check = checkRepository.save(check);

        try {

            if (!properties.isEnabled()) {

                return failCheck(
                    check,
                    "Credit bureau integration is disabled"
                );
            }

            validateConfiguration();

            CreditBureauRequest request = CreditBureauRequest.builder()
                .nationalId(
                    borrower.getNationalId() != null
                        ? borrower.getNationalId()
                        : ""
                )
                .firstName(borrower.getFirstName())
                .lastName(borrower.getLastName())
                .requestReference(check.getReference())
                .build();

            CreditBureauResponse response =
                requestCreditReport(request, check);

            if (response == null || !response.isRecordFound()) {

                check.setStatus(
                    CreditBureauCheck.CheckStatus.NO_RECORD_FOUND
                );

                check.setCompletedAt(LocalDateTime.now());

                check.setFailureReason(
                    "No credit bureau record found"
                );

                check = checkRepository.save(check);

                audit(
                    borrower,
                    borrowerId,
                    "CREDIT_BUREAU_NO_RECORD",
                    check
                );

                return check;
            }

            applyResponse(check, response);

            check.setStatus(
                CreditBureauCheck.CheckStatus.COMPLETED
            );

            check.setCompletedAt(LocalDateTime.now());

            check.setExpiresAt(
                LocalDateTime.now()
                    .plusDays(properties.getReportValidityDays())
            );

            check = checkRepository.save(check);

            updateBorrowerCreditFields(borrower, check);

            audit(
                borrower,
                borrowerId,
                "CREDIT_BUREAU_CHECK_COMPLETED",
                check
            );

            return check;

        } catch (Exception ex) {

            log.error(
                "Credit bureau check failed. borrowerId={}, reference={}",
                borrowerId,
                check.getReference(),
                ex
            );

            return failCheck(
                check,
                safeErrorMessage(ex)
            );
        }
    }

    /**
     * Real provider call.
     *
     * Retry only transient HTTP failures.
     */
    @Retryable(
        retryFor = {
            ResourceAccessException.class,
            HttpServerErrorException.class
        },
        maxAttemptsExpression =
            "#{@creditBureauProperties.maxAttempts}",
        backoff = @Backoff(
            delayExpression =
                "#{@creditBureauProperties.initialBackoffMillis}",
            multiplier = 2.0
        )
    )
    protected CreditBureauResponse requestCreditReport(
            CreditBureauRequest request,
            CreditBureauCheck check
    ) {

        check.setAttemptCount(
            check.getAttemptCount() == null
                ? 1
                : check.getAttemptCount() + 1
        );

        check.setLastAttemptAt(LocalDateTime.now());

        checkRepository.save(check);

        String url = buildUrl(
            properties.getCreditReportPath()
        );

        HttpHeaders headers = createHeaders(
            request.getRequestReference()
        );

        HttpEntity<CreditBureauRequest> entity =
            new HttpEntity<>(request, headers);

        ResponseEntity<Map> response =
            restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                Map.class
            );

        if (response.getStatusCode() == HttpStatus.NOT_FOUND) {

            return CreditBureauResponse.builder()
                .recordFound(false)
                .build();
        }

        if (!response.getStatusCode().is2xxSuccessful()) {

            throw new IllegalStateException(
                "Credit bureau returned HTTP " +
                response.getStatusCode().value()
            );
        }

        Map<?, ?> body = response.getBody();

        if (body == null) {

            throw new IllegalStateException(
                "Credit bureau returned an empty response"
            );
        }

        check.setProviderRequestId(
            stringValue(body.get("requestId"))
        );

        return mapProviderResponse(body);
    }

    /**
     * Report a disbursed loan.
     *
     * Production systems should make this idempotent.
     */
    @Transactional
    public void reportDisbursedLoan(
            Loan loan,
            String reportedBy
    ) {

        if (loan == null) {
            throw new IllegalArgumentException(
                "Loan is required"
            );
        }

        Borrower borrower = loan.getBorrower();

        if (borrower == null) {
            throw new IllegalArgumentException(
                "Loan borrower is required"
            );
        }

        if (!properties.isEnabled()) {

            log.info(
                "Credit bureau disabled. Loan {} not reported.",
                loan.getReferenceNumber()
            );

            return;
        }

        validateConfiguration();

        String externalReference =
            "DISBURSE-" + loan.getReferenceNumber();

        if (
            checkRepository.existsByExternalReference(
                externalReference
            )
        ) {

            log.info(
                "Loan {} already reported to credit bureau.",
                loan.getReferenceNumber()
            );

            return;
        }

        CreditBureauCheck check =
            CreditBureauCheck.builder()
                .borrower(borrower)
                .organization(borrower.getOrganization())
                .reference(generateReference(borrower))
                .externalReference(externalReference)
                .provider(properties.getProvider())
                .nationalIdChecked(borrower.getNationalId())
                .requestedBy(reportedBy)
                .status(CreditBureauCheck.CheckStatus.PROCESSING)
                .attemptCount(0)
                .build();

        checkRepository.save(check);

        try {

            sendLoanReport(loan, borrower, check);

            check.setStatus(
                CreditBureauCheck.CheckStatus.COMPLETED
            );

            check.setCompletedAt(LocalDateTime.now());

            checkRepository.save(check);

            auditService.log(
                borrower.getOrganization(),
                null,
                "CREDIT_BUREAU_LOAN_REPORTED",
                "LOAN",
                String.valueOf(loan.getId()),
                "Loan " +
                loan.getReferenceNumber() +
                " successfully reported to " +
                properties.getProvider()
            );

        } catch (Exception ex) {

            check.setStatus(
                CreditBureauCheck.CheckStatus.FAILED
            );

            check.setFailureReason(
                safeErrorMessage(ex)
            );

            check.setLastAttemptAt(
                LocalDateTime.now()
            );

            checkRepository.save(check);

            log.error(
                "Credit bureau loan reporting failed. loan={}",
                loan.getReferenceNumber(),
                ex
            );

            throw ex;
        }
    }

    @Retryable(
        retryFor = {
            ResourceAccessException.class,
            HttpServerErrorException.class
        },
        maxAttemptsExpression =
            "#{@creditBureauProperties.maxAttempts}",
        backoff = @Backoff(
            delayExpression =
                "#{@creditBureauProperties.initialBackoffMillis}",
            multiplier = 2.0
        )
    )
    protected void sendLoanReport(
            Loan loan,
            Borrower borrower,
            CreditBureauCheck check
    ) {

        check.setAttemptCount(
            check.getAttemptCount() == null
                ? 1
                : check.getAttemptCount() + 1
        );

        check.setLastAttemptAt(LocalDateTime.now());

        checkRepository.save(check);

        Map<String, Object> payload =
            new LinkedHashMap<>();

        payload.put(
            "requestReference",
            check.getReference()
        );

        payload.put(
            "loanNumber",
            loan.getReferenceNumber()
        );

        payload.put(
            "nationalId",
            borrower.getNationalId()
        );

        payload.put(
            "borrowerName",
            buildFullName(borrower)
        );

        payload.put(
            "loanAmount",
            loan.getAmount()
        );

        payload.put(
            "currency",
            loan.getCurrency()
        );

        payload.put(
            "status",
            loan.getStatus() != null
                ? loan.getStatus().name()
                : null
        );

        payload.put(
            "disbursedDate",
            loan.getDisbursedAt()
        );

        payload.put(
            "nextPaymentDate",
            loan.getNextPaymentDate()
        );

        HttpHeaders headers =
            createHeaders(check.getReference());

        HttpEntity<Map<String, Object>> entity =
            new HttpEntity<>(payload, headers);

        String url =
            buildUrl(properties.getLoanReportPath());

        ResponseEntity<String> response =
            restTemplate.postForEntity(
                url,
                entity,
                String.class
            );

        if (!response.getStatusCode().is2xxSuccessful()) {

            throw new IllegalStateException(
                "Credit bureau loan reporting returned HTTP " +
                response.getStatusCode().value()
            );
        }

        if (response.getBody() != null) {

            check.setRawResponse(
                response.getBody()
            );
        }
    }

    public List<CreditBureauCheck> getHistory(
            Long borrowerId,
            Long organizationId
    ) {

        return checkRepository
            .findByBorrower_IdAndOrganization_IdOrderByCreatedAtDesc(
                borrowerId,
                organizationId
            );
    }

    public Optional<CreditBureauCheck> getLatest(
            Long borrowerId,
            Long organizationId
    ) {

        return checkRepository
            .findFirstByBorrower_IdAndOrganization_IdOrderByCreatedAtDesc(
                borrowerId,
                organizationId
            );
    }

    private void validateConfiguration() {

        if (!properties.isEnabled()) {
            return;
        }

        if (
            properties.getBaseUrl() == null ||
            properties.getBaseUrl().isBlank()
        ) {

            throw new IllegalStateException(
                "Credit bureau base URL is not configured"
            );
        }

        if (
            properties.getApiKey() == null ||
            properties.getApiKey().isBlank()
        ) {

            throw new IllegalStateException(
                "Credit bureau API key is not configured"
            );
        }

        if (
            properties.getProvider() == null ||
            properties.getProvider().isBlank() ||
            properties.getProvider().equalsIgnoreCase("NONE")
        ) {

            throw new IllegalStateException(
                "Credit bureau provider is not configured"
            );
        }
    }

    private HttpHeaders createHeaders(
            String requestReference
    ) {

        HttpHeaders headers =
            new HttpHeaders();

        headers.setContentType(
            MediaType.APPLICATION_JSON
        );

        headers.setAccept(
            Collections.singletonList(
                MediaType.APPLICATION_JSON
            )
        );

        headers.setBearerAuth(
            properties.getApiKey()
        );

        headers.set(
            "X-Request-Reference",
            requestReference
        );

        return headers;
    }

    private String buildUrl(String path) {

        String base =
            properties.getBaseUrl();

        if (base.endsWith("/")) {
            base = base.substring(
                0,
                base.length() - 1
            );
        }

        if (path == null || path.isBlank()) {
            return base;
        }

        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        return UriComponentsBuilder
            .fromUriString(base + path)
            .build()
            .toUriString();
    }

    private void applyResponse(
            CreditBureauCheck check,
            CreditBureauResponse response
    ) {

        check.setCreditScore(
            response.getCreditScore()
        );

        check.setRiskGrade(
            response.getRiskGrade()
        );

        check.setActiveFacilities(
            response.getActiveFacilities()
        );

        check.setDelinquentAccounts(
            response.getDelinquentAccounts()
        );

        check.setTotalOutstandingDebt(
            response.getTotalOutstandingDebt()
        );

        check.setTotalMonthlyObligations(
            response.getTotalMonthlyObligations()
        );

        check.setHasDefaultHistory(
            response.getHasDefaultHistory()
        );

        check.setHasActiveListing(
            response.getHasActiveListing()
        );

        check.setListingReason(
            response.getListingReason()
        );

        check.setProviderRequestId(
            response.getProviderRequestId()
        );

        check.setProvider(
            properties.getProvider()
        );
    }

    private void updateBorrowerCreditFields(
            Borrower borrower,
            CreditBureauCheck check
    ) {

        if (
            check.getStatus() !=
                CreditBureauCheck.CheckStatus.COMPLETED
        ) {
            return;
        }

        if (check.getCreditScore() == null) {
            return;
        }

        borrower.setCreditScore(
            check.getCreditScore()
        );

        borrower.setCreditBureau(
            check.getProvider()
        );

        borrower.setCreditReportDate(
            LocalDate.now()
        );

        borrowerRepository.save(borrower);
    }

    private CreditBureauCheck failCheck(
            CreditBureauCheck check,
            String reason
    ) {

        check.setStatus(
            CreditBureauCheck.CheckStatus.FAILED
        );

        check.setFailureReason(
            truncate(reason, 1000)
        );

        check.setCompletedAt(
            LocalDateTime.now()
        );

        check = checkRepository.save(check);

        if (check.getBorrower() != null) {

            audit(
                check.getBorrower(),
                check.getBorrower().getId(),
                "CREDIT_BUREAU_CHECK_FAILED",
                check
            );
        }

        return check;
    }

    private void audit(
            Borrower borrower,
            Long borrowerId,
            String action,
            CreditBureauCheck check
    ) {

        try {

            auditService.log(
                borrower.getOrganization(),
                null,
                action,
                "BORROWER",
                String.valueOf(borrowerId),
                "Credit bureau provider=" +
                check.getProvider() +
                ", status=" +
                check.getStatus() +
                ", reference=" +
                check.getReference()
            );

        } catch (Exception ex) {

            /*
             * Audit failure should be logged but should not destroy
             * an already completed bureau operation.
             */
            log.error(
                "Failed to write credit bureau audit event",
                ex
            );
        }
    }

    private CreditBureauResponse mapProviderResponse(
            Map<?, ?> body
    ) {

        boolean recordFound =
            !Boolean.FALSE.equals(
                body.get("recordFound")
            );

        return CreditBureauResponse.builder()
            .recordFound(recordFound)
            .providerRequestId(
                stringValue(body.get("requestId"))
            )
            .creditScore(
                integerValue(body.get("creditScore"))
            )
            .riskGrade(
                stringValue(body.get("riskGrade"))
            )
            .activeFacilities(
                integerValue(body.get("activeFacilities"))
            )
            .delinquentAccounts(
                integerValue(body.get("delinquentAccounts"))
            )
            .totalOutstandingDebt(
                decimalValue(
                    body.get("totalOutstandingDebt")
                )
            )
            .totalMonthlyObligations(
                decimalValue(
                    body.get("totalMonthlyObligations")
                )
            )
            .hasDefaultHistory(
                booleanValue(
                    body.get("hasDefaultHistory")
                )
            )
            .hasActiveListing(
                booleanValue(
                    body.get("hasActiveListing")
                )
            )
            .listingReason(
                stringValue(body.get("listingReason"))
            )
            .build();
    }

    private String generateReference(
            Borrower borrower
    ) {

        String country = "XX";

        if (
            borrower.getOrganization() != null &&
            borrower.getOrganization().getCountry() != null
        ) {

            country =
                borrower.getOrganization()
                    .getCountry()
                    .toString();
        }

        return "CRB-" +
               country +
               "-" +
               UUID.randomUUID();
    }

    private String buildFullName(
            Borrower borrower
    ) {

        String first =
            borrower.getFirstName() == null
                ? ""
                : borrower.getFirstName().trim();

        String last =
            borrower.getLastName() == null
                ? ""
                : borrower.getLastName().trim();

        return (first + " " + last).trim();
    }

    private String safeErrorMessage(
            Exception ex
    ) {

        String message =
            ex.getMessage();

        if (message == null ||
            message.isBlank()) {

            return ex.getClass()
                .getSimpleName();
        }

        return truncate(message, 1000);
    }

    private String truncate(
            String value,
            int max
    ) {

        if (value == null) {
            return null;
        }

        return value.length() <= max
            ? value
            : value.substring(0, max);
    }

    private String stringValue(Object value) {

        return value == null
            ? null
            : value.toString();
    }

    private Integer integerValue(Object value) {

        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.intValue();
        }

        try {
            return Integer.valueOf(
                value.toString()
            );
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private BigDecimal decimalValue(
            Object value
    ) {

        if (value == null) {
            return null;
        }

        try {

            return new BigDecimal(
                value.toString()
            );

        } catch (NumberFormatException ex) {

            return null;
        }
    }

    private Boolean booleanValue(
            Object value
    ) {

        if (value == null) {
            return null;
        }

        if (value instanceof Boolean b) {
            return b;
        }

        return Boolean.valueOf(
            value.toString()
        );
    }
}
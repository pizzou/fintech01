package com.patrick.fintech.loan_backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patrick.fintech.loan_backend.config.CreditBureauProperties;
import com.patrick.fintech.loan_backend.dto.creditbureau.CreditBureauRequest;
import com.patrick.fintech.loan_backend.dto.creditbureau.CreditBureauResponse;
import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.CreditBureauCheck;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.repository.BorrowerRepository;
import com.patrick.fintech.loan_backend.repository.CreditBureauCheckRepository;
import com.patrick.fintech.loan_backend.repository.LoanRepository;

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
@Transactional
public class CreditBureauService {

    private final BorrowerRepository borrowerRepository;
    private final LoanRepository loanRepository;

    public Object checkBorrower(Long borrowerId) {

        Borrower borrower =
                borrowerRepository
                        .findById(borrowerId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Borrower not found"
                                )
                        );

        Organization organization =
                borrower.getOrganization();

        if (organization == null) {
            throw new IllegalStateException(
                    "Organization information is missing from this borrower."
            );
        }

        /*
         * Your existing credit-bureau logic goes here.
         *
         * IMPORTANT:
         * use `organization` obtained above.
         */

        return performCreditCheck(
                borrower,
                organization
        );
    }

    private Object performCreditCheck(
            Borrower borrower,
            Organization organization
    ) {

        /*
         * Keep your existing implementation here.
         *
         * Example only:
         */

        Map<String, Object> result =
                new HashMap<>();

        result.put(
                "provider",
                "INTERNAL_SIMULATED"
        );

        result.put(
                "creditScore",
                borrower.getCreditScore()
        );

        result.put(
                "riskGrade",
                "ESTIMATE"
        );

        result.put(
                "borrowerId",
                borrower.getId()
        );

        result.put(
                "organizationId",
                organization.getId()
        );

        return result;
    }

    @Transactional(readOnly = true)
    public Object history(Long borrowerId) {

        Borrower borrower =
                borrowerRepository
                        .findById(borrowerId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Borrower not found"
                                )
                        );

        Organization organization =
                borrower.getOrganization();

        if (organization == null) {
            throw new IllegalStateException(
                    "Organization information is missing from this borrower."
            );
        }

        // Your existing history implementation.
        return List.of();
    }

    @Transactional(readOnly = true)
    public Object latest(Long borrowerId) {

        Borrower borrower =
                borrowerRepository
                        .findById(borrowerId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Borrower not found"
                                )
                        );

        Organization organization =
                borrower.getOrganization();

        if (organization == null) {
            throw new IllegalStateException(
                    "Organization information is missing from this borrower."
            );
        }

        // Your existing latest-report implementation.
        return null;
    }

    @Transactional(readOnly = true)
    public Object reportForLoan(Long loanId) {

        Loan loan =
                loanRepository
                        .findById(loanId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Loan not found"
                                )
                        );

        Organization organization =
                loan.getOrganization();

        if (organization == null) {
            throw new IllegalStateException(
                    "Organization information is missing from this loan."
            );
        }

        Borrower borrower =
                loan.getBorrower();

        if (borrower == null) {
            throw new IllegalStateException(
                    "Borrower information is missing from this loan."
            );
        }

        // Your existing report implementation.
        return null;
    }

    public Object retryReport(Long loanId) {

        Loan loan =
                loanRepository
                        .findById(loanId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Loan not found"
                                )
                        );

        Organization organization =
                loan.getOrganization();

        if (organization == null) {
            throw new IllegalStateException(
                    "Organization information is missing from this loan."
            );
        }

        Borrower borrower =
                loan.getBorrower();

        if (borrower == null) {
            throw new IllegalStateException(
                    "Borrower information is missing from this loan."
            );
        }

        // Your existing retry implementation.
        return performCreditCheck(
                borrower,
                organization
        );
    }
}
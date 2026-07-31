package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.CreditBureauCheck;
import com.patrick.fintech.loan_backend.repository.BorrowerRepository;
import com.patrick.fintech.loan_backend.service.CreditBureauService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/credit-bureau")
@RequiredArgsConstructor
@Slf4j
public class CreditBureauController {

    private final CreditBureauService creditBureauService;
    private final BorrowerRepository borrowerRepository;

    // ============================================================
    // RUN CREDIT BUREAU CHECK
    // ============================================================

    @PostMapping("/borrowers/{borrowerId}/check")
    public ResponseEntity<?> runCheck(
            @PathVariable Long borrowerId,
            @RequestParam(required = false) Long organizationId,
            @RequestParam(required = false) String requestedBy,
            Authentication authentication
    ) {

        try {

            Borrower borrower =
                    borrowerRepository
                            .findById(borrowerId)
                            .orElse(null);

            if (borrower == null) {

                return ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body(Map.of(
                                "success", false,
                                "message", "Borrower not found"
                        ));
            }

            // ----------------------------------------------------
            // Resolve organization from borrower if frontend
            // did not provide organizationId.
            // ----------------------------------------------------

            if (organizationId == null) {

                if (borrower.getOrganization() == null ||
                    borrower.getOrganization().getId() == null) {

                    return ResponseEntity
                            .status(HttpStatus.FORBIDDEN)
                            .body(Map.of(
                                    "success", false,
                                    "message",
                                    "Organization information is missing from this borrower"
                            ));
                }

                organizationId =
                        borrower.getOrganization().getId();
            }

            // ----------------------------------------------------
            // Security check:
            // borrower must belong to the resolved organization.
            // ----------------------------------------------------

            if (borrower.getOrganization() == null ||
                borrower.getOrganization().getId() == null ||
                !borrower.getOrganization().getId()
                        .equals(organizationId)) {

                return ResponseEntity
                        .status(HttpStatus.FORBIDDEN)
                        .body(Map.of(
                                "success", false,
                                "message",
                                "Borrower does not belong to this organization"
                        ));
            }

            // ----------------------------------------------------
            // Resolve requesting user.
            // ----------------------------------------------------

            if (requestedBy == null ||
                requestedBy.isBlank()) {

                requestedBy =
                        authentication != null &&
                        authentication.getName() != null
                                ? authentication.getName()
                                : "SYSTEM";
            }

            log.info(
                    "Running credit bureau check. borrowerId={}, organizationId={}, requestedBy={}",
                    borrowerId,
                    organizationId,
                    requestedBy
            );

            // ----------------------------------------------------
            // Run service.
            // ----------------------------------------------------

            CreditBureauCheck result =
                    creditBureauService.runCheck(
                            borrowerId,
                            organizationId,
                            requestedBy
                    );

            return ResponseEntity.ok(
                    Map.of(
                            "success", true,
                            "data", result
                    )
            );

        } catch (SecurityException ex) {

            log.warn(
                    "Credit bureau security failure. borrowerId={}",
                    borrowerId,
                    ex
            );

            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "success", false,
                            "message",
                            ex.getMessage() != null
                                    ? ex.getMessage()
                                    : "Access denied"
                    ));

        } catch (IllegalArgumentException ex) {

            log.warn(
                    "Credit bureau invalid request. borrowerId={}",
                    borrowerId,
                    ex
            );

            return ResponseEntity
                    .badRequest()
                    .body(Map.of(
                            "success", false,
                            "message",
                            ex.getMessage() != null
                                    ? ex.getMessage()
                                    : "Invalid request"
                    ));

        } catch (Exception ex) {

            log.error(
                    "Credit bureau check failed. borrowerId={}",
                    borrowerId,
                    ex
            );

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "message",
                            "Credit bureau check failed"
                    ));
        }
    }

    // ============================================================
    // HISTORY
    // ============================================================

    @GetMapping("/borrowers/{borrowerId}/history")
    public ResponseEntity<?> history(
            @PathVariable Long borrowerId,
            @RequestParam(required = false) Long organizationId
    ) {

        try {

            organizationId =
                    resolveOrganizationId(
                            borrowerId,
                            organizationId
                    );

            List<CreditBureauCheck> history =
                    creditBureauService.getHistory(
                            borrowerId,
                            organizationId
                    );

            return ResponseEntity.ok(
                    Map.of(
                            "success", true,
                            "data", history
                    )
            );

        } catch (SecurityException ex) {

            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "success", false,
                            "message", ex.getMessage()
                    ));

        } catch (Exception ex) {

            log.error(
                    "Failed to load credit bureau history. borrowerId={}",
                    borrowerId,
                    ex
            );

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "message",
                            "Unable to load credit bureau history"
                    ));
        }
    }

    // ============================================================
    // LATEST
    // ============================================================

    @GetMapping("/borrowers/{borrowerId}/latest")
    public ResponseEntity<?> latest(
            @PathVariable Long borrowerId,
            @RequestParam(required = false) Long organizationId
    ) {

        try {

            organizationId =
                    resolveOrganizationId(
                            borrowerId,
                            organizationId
                    );

            return creditBureauService
                    .getLatest(
                            borrowerId,
                            organizationId
                    )
                    .map(check ->
                            ResponseEntity.ok(
                                    Map.of(
                                            "success", true,
                                            "data", check
                                    )
                            )
                    )
                    .orElseGet(() ->
                            ResponseEntity.ok(
                                    Map.of(
                                            "success", true
                                    )
                            )
                    );

        } catch (SecurityException ex) {

            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "success", false,
                            "message", ex.getMessage()
                    ));

        } catch (Exception ex) {

            log.error(
                    "Failed to load latest credit bureau check. borrowerId={}",
                    borrowerId,
                    ex
            );

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "message",
                            "Unable to load latest credit bureau check"
                    ));
        }
    }

    // ============================================================
    // ORGANIZATION RESOLUTION
    // ============================================================

    private Long resolveOrganizationId(
            Long borrowerId,
            Long organizationId
    ) {

        Borrower borrower =
                borrowerRepository
                        .findById(borrowerId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Borrower not found: " +
                                        borrowerId
                                )
                        );

        if (borrower.getOrganization() == null ||
            borrower.getOrganization().getId() == null) {

            throw new SecurityException(
                    "Organization information is missing from this borrower"
            );
        }

        Long borrowerOrganizationId =
                borrower.getOrganization().getId();

        // If no organization was sent by frontend,
        // use the borrower's organization.
        if (organizationId == null) {
            return borrowerOrganizationId;
        }

        // If frontend sent an organization, verify it.
        if (!borrowerOrganizationId.equals(organizationId)) {

            throw new SecurityException(
                    "Borrower does not belong to this organization"
            );
        }

        return organizationId;
    }
}
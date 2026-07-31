package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.model.CreditBureauCheck;
import com.patrick.fintech.loan_backend.service.CreditBureauService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/credit-bureau")
@RequiredArgsConstructor
public class CreditBureauController {

    private final CreditBureauService creditBureauService;

    @PostMapping("/borrowers/{borrowerId}/check")
    public ResponseEntity<?> check(
            @PathVariable Long borrowerId
    ) {
        return ResponseEntity.ok(
                creditBureauService.checkBorrower(
                        borrowerId
                )
        );
    }

    @GetMapping("/borrowers/{borrowerId}/history")
    public ResponseEntity<?> history(
            @PathVariable Long borrowerId
    ) {
        return ResponseEntity.ok(
                creditBureauService.history(
                        borrowerId
                )
        );
    }

    @GetMapping("/borrowers/{borrowerId}/latest")
    public ResponseEntity<?> latest(
            @PathVariable Long borrowerId
    ) {
        return ResponseEntity.ok(
                creditBureauService.latest(
                        borrowerId
                )
        );
    }

    @GetMapping("/loans/{loanId}/report")
    public ResponseEntity<?> reportForLoan(
            @PathVariable Long loanId
    ) {
        return ResponseEntity.ok(
                creditBureauService.reportForLoan(
                        loanId
                )
        );
    }

    @PostMapping("/loans/{loanId}/report/retry")
    public ResponseEntity<?> retryReport(
            @PathVariable Long loanId
    ) {
        return ResponseEntity.ok(
                creditBureauService.retryReport(
                        loanId
                )
        );
    }
}
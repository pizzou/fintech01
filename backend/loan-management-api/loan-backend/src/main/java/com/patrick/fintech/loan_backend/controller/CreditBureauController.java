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
    private final CurrentUserUtil currentUserUtil;

    @PostMapping("/borrowers/{borrowerId}/check")
    public ResponseEntity<?> runCheck(
            @PathVariable Long borrowerId,
            @RequestParam(required = false) String requestedBy
    ) {

        Long organizationId = currentUserUtil.getCurrentOrganizationId();

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
    }

    @GetMapping("/borrowers/{borrowerId}/history")
    public ResponseEntity<?> history(
            @PathVariable Long borrowerId
    ) {

        Long organizationId = currentUserUtil.getCurrentOrganizationId();

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
    }

    @GetMapping("/borrowers/{borrowerId}/latest")
    public ResponseEntity<?> latest(
            @PathVariable Long borrowerId
    ) {

        Long organizationId = currentUserUtil.getCurrentOrganizationId();

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
                        "success", true,
                        "data", null
                    )
                )
            );
    }
}
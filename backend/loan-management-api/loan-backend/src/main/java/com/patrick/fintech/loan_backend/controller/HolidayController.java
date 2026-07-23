package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.model.Holiday;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.repository.OrganizationRepository;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.service.HolidayService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/holidays")
@RequiredArgsConstructor
public class HolidayController {

    private final HolidayService holidayService;
    private final OrganizationRepository orgRepo;
    private final CurrentUserUtil currentUserUtil;
    private final AuditService auditService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Holiday>>> list() {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        return ResponseEntity.ok(ApiResponse.ok(holidayService.list(orgId)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<Holiday>> create(@RequestBody Map<String, Object> body) {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        Organization org = orgRepo.findById(orgId).orElseThrow(() -> new RuntimeException("Organization not found"));

        LocalDate date = LocalDate.parse((String) body.get("holidayDate"));
        String name = (String) body.get("name");
        boolean recurring = Boolean.TRUE.equals(body.get("recurringAnnually"));

        Holiday created = holidayService.create(org, date, name, recurring);
        auditService.log(org, currentUserUtil.getCurrentUser(), "HOLIDAY_CREATED", "HOLIDAY",
            String.valueOf(created.getId()), "Added holiday: " + name + " (" + date + ")", null, null, "Administration");

        return ResponseEntity.ok(ApiResponse.ok("Holiday added", created));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        holidayService.delete(orgId, id);
        auditService.log(orgRepo.findById(orgId).orElse(null), currentUserUtil.getCurrentUser(),
            "HOLIDAY_DELETED", "HOLIDAY", String.valueOf(id), "Removed holiday", null, null, "Administration");
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.*;
import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.BorrowerRepository;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.service.MailService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/borrowers")
@RequiredArgsConstructor
public class BorrowerController {

    private final BorrowerRepository borrowerRepo;
    private final CurrentUserUtil    currentUserUtil;
    private final AuditService       auditService;
    private final MailService        mailService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<Borrower>>> list(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false)    String q) {
        Organization org = currentUserUtil.getCurrentUser().getOrganization();
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Borrower> result = (q != null && !q.isBlank())
            ? borrowerRepo.search(org, q, pageable)
            : borrowerRepo.findByOrganization(org, pageable);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Borrower>> get(@PathVariable Long id) {
        Organization org = currentUserUtil.getCurrentUser().getOrganization();
        Borrower b = borrowerRepo.findById(id)
            .orElseThrow(() -> new RuntimeException("Borrower not found: " + id));
        if (!b.getOrganization().getId().equals(org.getId()))
            throw new RuntimeException("Access denied");
        return ResponseEntity.ok(ApiResponse.ok(b));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Borrower>> create(@Valid @RequestBody BorrowerRequest req) {
        Organization org = currentUserUtil.getCurrentUser().getOrganization();

        // Check email, phone, and national ID for an existing match — previously only email
        // was checked, and even then it just threw a dead-end error. If this is genuinely the
        // same person (e.g. they already applied via the public website), staff need a path
        // forward: view that existing profile and add a new loan there, not a wall.
        if (req.getEmail() != null && !req.getEmail().isBlank()) {
            var existing = borrowerRepo.findByEmailAndOrganization_Id(req.getEmail(), org.getId());
            if (existing.isPresent())
                throw new com.patrick.fintech.loan_backend.exception.DuplicateBorrowerException(
                    "A borrower with this email already exists: " + existing.get().getFirstName() + " " + existing.get().getLastName(),
                    existing.get(), "email");
        }
        if (req.getPhone() != null && !req.getPhone().isBlank()) {
            var existing = borrowerRepo.findByPhoneHashAndOrganization_Id(
                com.patrick.fintech.loan_backend.security.HmacIndexer.index(req.getPhone()), org.getId());
            if (existing.isPresent())
                throw new com.patrick.fintech.loan_backend.exception.DuplicateBorrowerException(
                    "A borrower with this phone number already exists: " + existing.get().getFirstName() + " " + existing.get().getLastName(),
                    existing.get(), "phone number");
        }
        if (req.getNationalId() != null && !req.getNationalId().isBlank()) {
            var existing = borrowerRepo.findByNationalIdHashAndOrganization_Id(
                com.patrick.fintech.loan_backend.security.HmacIndexer.index(req.getNationalId()), org.getId());
            if (existing.isPresent())
                throw new com.patrick.fintech.loan_backend.exception.DuplicateBorrowerException(
                    "A borrower with this national ID already exists: " + existing.get().getFirstName() + " " + existing.get().getLastName(),
                    existing.get(), "national ID");
        }

        Borrower b = Borrower.builder()
            .organization(org)
            .firstName(req.getFirstName()).lastName(req.getLastName())
            .email(req.getEmail()).phone(req.getPhone()).alternatePhone(req.getAlternatePhone())
            .nationalId(req.getNationalId()).passportNumber(req.getPassportNumber())
            .taxIdentificationNumber(req.getTaxIdentificationNumber())
            .dateOfBirth(req.getDateOfBirth() != null ? LocalDate.parse(req.getDateOfBirth()) : null)
            .gender(req.getGender()).maritalStatus(req.getMaritalStatus())
            .nationality(req.getNationality())
            .addressLine1(req.getAddressLine1()).addressLine2(req.getAddressLine2())
            .city(req.getCity()).stateProvince(req.getStateProvince())
            .postalCode(req.getPostalCode()).country(req.getCountry())
            .employerName(req.getEmployerName()).employmentType(req.getEmploymentType())
            .jobTitle(req.getJobTitle()).monthlyIncome(req.getMonthlyIncome())
            .monthlyExpenses(req.getMonthlyExpenses()).netWorth(req.getNetWorth())
            .creditScore(req.getCreditScore()).creditBureau(req.getCreditBureau())
            .bankName(req.getBankName()).bankAccountNumber(req.getBankAccountNumber())
            .bankBranch(req.getBankBranch())
            .status(Borrower.BorrowerStatus.ACTIVE)
            .build();
        Borrower saved = borrowerRepo.save(b);
        auditService.log(org, currentUserUtil.getCurrentUser(), "BORROWER_CREATED", "BORROWER",
            String.valueOf(saved.getId()), "Created borrower " + saved.getFirstName() + " " + saved.getLastName());
        try { mailService.sendBorrowerWelcome(saved); } catch (Exception e) { /* best-effort, never blocks borrower creation */ }
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Borrower created", saved));
    }

    @PostMapping("/{id}/blacklist")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN','MANAGER','AUDITOR')")
    public ResponseEntity<ApiResponse<Borrower>> blacklist(@PathVariable Long id, @RequestBody java.util.Map<String, String> body) {
        Organization org = currentUserUtil.getCurrentUser().getOrganization();
        Borrower borrower = borrowerRepo.findById(id)
            .filter(b -> b.getOrganization().getId().equals(org.getId()))
            .orElseThrow(() -> new RuntimeException("Borrower not found"));

        String reason = body.get("reason");
        if (reason == null || reason.isBlank())
            throw new RuntimeException("A reason is required to blacklist a borrower");

        borrower.setStatus(Borrower.BorrowerStatus.BLACKLISTED);
        borrower.setBlacklistReason(reason);
        borrower.setBlacklistedAt(java.time.LocalDateTime.now());
        borrower.setBlacklistedBy(currentUserUtil.getCurrentUser());
        borrowerRepo.save(borrower);

        auditService.log(org, currentUserUtil.getCurrentUser(), "BORROWER_BLACKLISTED", "BORROWER",
            String.valueOf(borrower.getId()), "Blacklisted: " + reason, null, null, "Borrower Management");

        return ResponseEntity.ok(ApiResponse.ok(borrower));
    }

    @PostMapping("/{id}/unblacklist")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<Borrower>> unblacklist(@PathVariable Long id) {
        Organization org = currentUserUtil.getCurrentUser().getOrganization();
        Borrower borrower = borrowerRepo.findById(id)
            .filter(b -> b.getOrganization().getId().equals(org.getId()))
            .orElseThrow(() -> new RuntimeException("Borrower not found"));

        borrower.setStatus(Borrower.BorrowerStatus.ACTIVE);
        borrower.setBlacklistReason(null);
        borrower.setBlacklistedAt(null);
        borrower.setBlacklistedBy(null);
        borrowerRepo.save(borrower);

        auditService.log(org, currentUserUtil.getCurrentUser(), "BORROWER_UNBLACKLISTED", "BORROWER",
            String.valueOf(borrower.getId()), "Removed from blacklist", null, null, "Borrower Management");

        return ResponseEntity.ok(ApiResponse.ok(borrower));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Borrower>> update(
            @PathVariable Long id, @RequestBody BorrowerRequest req) {
        Organization org = currentUserUtil.getCurrentUser().getOrganization();
        Borrower b = borrowerRepo.findById(id)
            .orElseThrow(() -> new RuntimeException("Borrower not found"));
        if (!b.getOrganization().getId().equals(org.getId()))
            throw new RuntimeException("Access denied");
        if (req.getFirstName() != null) b.setFirstName(req.getFirstName());
        if (req.getLastName()  != null) b.setLastName(req.getLastName());
        if (req.getPhone()     != null) b.setPhone(req.getPhone());
        if (req.getMonthlyIncome() != null) b.setMonthlyIncome(req.getMonthlyIncome());
        if (req.getCreditScore()   != null) b.setCreditScore(req.getCreditScore());
        if (req.getEmployerName()  != null) b.setEmployerName(req.getEmployerName());
        if (req.getEmploymentType()!= null) b.setEmploymentType(req.getEmploymentType());
        Borrower saved = borrowerRepo.save(b);
        auditService.log(org, currentUserUtil.getCurrentUser(), "BORROWER_UPDATED", "BORROWER",
            String.valueOf(saved.getId()), "Updated borrower " + saved.getFirstName() + " " + saved.getLastName());
        return ResponseEntity.ok(ApiResponse.ok("Borrower updated", saved));
    }
}
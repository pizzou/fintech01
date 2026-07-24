package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.InternalDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class InternalDocumentService {

    private final InternalDocumentRepository docRepo;
    private final AuditService auditService;

    // Broader than BorrowerFileService's allow-list on purpose — internal docs are policies/
    // contracts/spreadsheets, not just scanned IDs, so Office formats are expected here.
    private static final Set<String> ALLOWED_TYPES = Set.of(
        "application/pdf", "image/jpeg", "image/jpg", "image/png", "image/webp",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "text/plain", "text/csv");
    private static final long MAX_FILE_BYTES = 20L * 1024 * 1024; // 20MB — contracts/board packs run longer than a scanned ID

    public static final Set<String> CATEGORIES = Set.of(
        "POLICY", "CONTRACT", "MEMO", "TEMPLATE", "BOARD_MINUTES", "COMPLIANCE", "OTHER");

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new RuntimeException("No file was received.");
        if (file.getSize() > MAX_FILE_BYTES) throw new RuntimeException("File is too large — please upload something under 20MB.");
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new RuntimeException("Unsupported file type. Please upload a PDF, Word, Excel, image, or text file.");
        }
    }

   public InternalDocument upload(Organization org, User uploadedBy, MultipartFile file,
                                    String title, String category, String description) throws IOException {
        if (org == null) throw new RuntimeException("Could not determine your organization — please sign out and back in, then try again.");
        validate(file);

        String cat = (category != null && CATEGORIES.contains(category.toUpperCase())) ? category.toUpperCase() : "OTHER";

        // file.getOriginalFilename() can come back null or blank in some browsers/upload paths
        // (drag-and-drop from certain sources, some mobile webviews). title/fileName are both
        // NOT NULL columns in the database -- previously, a null here silently reached Postgres
        // and came back as a generic "conflicts with an existing record" error that gave no hint
        // what was actually wrong. This resolves a safe fallback instead of ever sending null,
        // and validates title explicitly so a real problem surfaces as a clear message here
        // instead of a masked database error later.
        String safeFileName = (file.getOriginalFilename() != null && !file.getOriginalFilename().isBlank())
            ? file.getOriginalFilename()
            : "document-" + System.currentTimeMillis();

        String resolvedTitle = (title != null && !title.isBlank())
            ? title
            : safeFileName;

        if (resolvedTitle == null || resolvedTitle.isBlank())
            throw new RuntimeException("Please enter a title for this document, or choose a file with a valid file name.");
        if (file.getContentType() == null || file.getContentType().isBlank())
            throw new RuntimeException("Could not determine this file's type. Please try a different file.");
        if (file.getSize() <= 0)
            throw new RuntimeException("This file appears to be empty.");

        InternalDocument doc = InternalDocument.builder()
            .organization(org)
            .title(resolvedTitle)
            .category(cat)
            .description(description)
            .fileName(safeFileName)
            .fileType(file.getContentType())
            .fileSize(file.getSize())
            .data(file.getBytes())
            .uploadedBy(uploadedBy)
            .build();

        doc = docRepo.save(doc);
        auditService.log(org, uploadedBy, "INTERNAL_DOCUMENT_UPLOADED", "INTERNAL_DOCUMENT",
            String.valueOf(doc.getId()), "Uploaded \"" + doc.getTitle() + "\" (" + cat + ")",
            null, null, "Documents & KYC");
        return doc;
    }

    public List<InternalDocumentRepository.Summary> list(Long orgId, String category) {
        if (category != null && !category.isBlank()) return docRepo.findSummariesByOrgAndCategory(orgId, category.toUpperCase());
        return docRepo.findSummariesByOrg(orgId);
    }

    /** Enforces org-scoping the same way BorrowerFileService.getByIdForOrg does — a file ID
     *  belonging to another organization is treated as not found, not merely forbidden, so it
     *  doesn't confirm the ID's existence to an unauthorized caller. */
    public InternalDocument getByIdForOrg(Long id, Long orgId) {
        InternalDocument doc = docRepo.findById(id).orElseThrow(() -> new RuntimeException("Document not found: " + id));
        if (!doc.getOrganization().getId().equals(orgId)) throw new RuntimeException("Document not found: " + id);
        return doc;
    }

    public void delete(Long id, Long orgId, User deletedBy) {
        InternalDocument doc = getByIdForOrg(id, orgId);
        docRepo.deleteById(id);
        auditService.log(doc.getOrganization(), deletedBy, "INTERNAL_DOCUMENT_DELETED", "INTERNAL_DOCUMENT",
            String.valueOf(id), "Deleted \"" + doc.getTitle() + "\"", null, null, "Documents & KYC");
    }
}

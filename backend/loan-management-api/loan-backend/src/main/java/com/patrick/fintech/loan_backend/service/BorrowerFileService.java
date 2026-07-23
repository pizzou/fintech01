package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;
import java.util.Set;

@Service
public class BorrowerFileService {

    private final BorrowerFileRepository fileRepository;
    private final BorrowerRepository borrowerRepository;

    public BorrowerFileService(BorrowerFileRepository f, BorrowerRepository b) {
        this.fileRepository = f;
        this.borrowerRepository = b;
    }

    private static final Set<String> ALLOWED_TYPES = Set.of(
        "application/pdf", "image/jpeg", "image/jpg", "image/png", "image/webp");
    private static final long MAX_FILE_BYTES = 8L * 1024 * 1024; // 8MB — comfortably fits a scanned statement/ID photo

    public static final Set<String> DOCUMENT_TYPES = Set.of(
        "NATIONAL_ID", "PASSPORT", "DRIVING_LICENSE", "PROOF_OF_ADDRESS", "BANK_STATEMENT",
        "PAYSLIP", "EMPLOYMENT_LETTER", "BUSINESS_REGISTRATION", "COLLATERAL_DOCUMENT",
        "SINGLE_CERTIFICATE", "MARRIAGE_CERTIFICATE", "SELFIE", "OTHER");

    public static final Set<String> VERIFICATION_STATUSES = Set.of(
        "PENDING_VERIFICATION", "VERIFIED", "REJECTED", "REPLACEMENT_REQUESTED");

    /** Validates content-type and size before ever reading the file into memory. */
    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new RuntimeException("No file was received.");
        if (file.getSize() > MAX_FILE_BYTES) throw new RuntimeException("File is too large — please upload something under 8MB.");
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new RuntimeException("Unsupported file type. Please upload a PDF, JPG, PNG, or WEBP file.");
        }
    }

    public BorrowerFile upload(Long borrowerId, MultipartFile file, String documentType, boolean byApplicant) throws IOException {
        validate(file);
        Borrower borrower = borrowerRepository.findById(borrowerId)
            .orElseThrow(() -> new RuntimeException("Borrower not found: " + borrowerId));
        BorrowerFile bf = new BorrowerFile();
        bf.setFileName(file.getOriginalFilename());
        bf.setFileType(file.getContentType());
        bf.setFileSize(file.getSize());
        bf.setData(file.getBytes());
        bf.setBorrower(borrower);
        bf.setDocumentType(DOCUMENT_TYPES.contains(documentType) ? documentType : "OTHER");
        bf.setUploadedByApplicant(byApplicant);
        return fileRepository.save(bf);
    }

    // Kept for any existing internal call sites that don't care about document type/source.
    public BorrowerFile upload(Long borrowerId, MultipartFile file) throws IOException {
        return upload(borrowerId, file, "OTHER", false);
    }

    public List<BorrowerFile> getByBorrower(Long borrowerId) {
        return fileRepository.findByBorrowerId(borrowerId);
    }

    /** Same as getByBorrower but strips the file bytes — for list/checklist views that don't need the content. */
    public List<BorrowerFile> getByBorrowerMetadataOnly(Long borrowerId) {
        List<BorrowerFile> files = fileRepository.findByBorrowerId(borrowerId);
        files.forEach(f -> f.setData(null));
        return files;
    }

    public BorrowerFile getById(Long fileId) {
        return fileRepository.findById(fileId)
            .orElseThrow(() -> new RuntimeException("File not found: " + fileId));
    }

    /** Same as getById, but throws if the file's borrower isn't in the caller's organization —
     *  without this, any authenticated staff user could fetch/download any other tenant's
     *  borrower documents by guessing a file ID. */
    public BorrowerFile getByIdForOrg(Long fileId, Long orgId) {
        BorrowerFile file = getById(fileId);
        if (file.getBorrower() == null || file.getBorrower().getOrganization() == null
                || !file.getBorrower().getOrganization().getId().equals(orgId)) {
            throw new RuntimeException("File not found: " + fileId);
        }
        return file;
    }

    /** Staff verification decision on a single uploaded document. */
    public BorrowerFile verify(Long fileId, Long orgId, String status, String comment, String officerName) {
        if (!VERIFICATION_STATUSES.contains(status))
            throw new RuntimeException("Unknown verification status: " + status);
        BorrowerFile file = getByIdForOrg(fileId, orgId);
        file.setVerificationStatus(status);
        file.setOfficerComment(comment);
        file.setVerifiedByName(officerName);
        file.setVerifiedAt(java.time.LocalDateTime.now());
        return fileRepository.save(file);
    }

    public void delete(Long fileId) {
        fileRepository.deleteById(fileId);
    }

    /** Required document types for which this borrower has NOT uploaded anything at all yet.
     *  Used to gate loan approval — a completely undocumented application shouldn't reach Approved. */
    public List<String> getMissingDocumentTypes(Long borrowerId, List<String> required) {
        if (required == null || required.isEmpty()) return List.of();
        Set<String> present = fileRepository.findByBorrowerId(borrowerId).stream()
            .map(BorrowerFile::getDocumentType)
            .collect(java.util.stream.Collectors.toSet());
        return required.stream().filter(t -> !present.contains(t)).toList();
    }

    /** Required document types that are missing OR uploaded-but-not-yet-VERIFIED by staff.
     *  Used to gate disbursement — money shouldn't move on a document an officer hasn't actually checked. */
    public List<String> getUnverifiedDocumentTypes(Long borrowerId, List<String> required) {
        if (required == null || required.isEmpty()) return List.of();
        Set<String> verified = fileRepository.findByBorrowerId(borrowerId).stream()
            .filter(f -> "VERIFIED".equals(f.getVerificationStatus()))
            .map(BorrowerFile::getDocumentType)
            .collect(java.util.stream.Collectors.toSet());
        return required.stream().filter(t -> !verified.contains(t)).toList();
    }
}

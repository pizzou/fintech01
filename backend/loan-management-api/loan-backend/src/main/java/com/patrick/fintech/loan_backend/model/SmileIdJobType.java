package com.patrick.fintech.loan_backend.model;

/**
 * Smile ID's REST API identifies job types by number, not name. These map
 * to the products described in Smile ID's docs (docs.usesmileid.com).
 * BIOMETRIC_KYC (1) is the one that covers your steps 1-4: document upload,
 * selfie capture, face match, and liveness detection all in a single job.
 */
public enum SmileIdJobType {
    BIOMETRIC_KYC(1),          // Document + selfie + face match + liveness -- steps 1-4
    DOCUMENT_VERIFICATION(6),  // Document only, no selfie
    ENHANCED_KYC(5),           // ID-number lookup against government DB -- no images
    BASIC_KYC(4);              // Lightweight ID-number check

    public final int code;
    SmileIdJobType(int code) { this.code = code; }
}
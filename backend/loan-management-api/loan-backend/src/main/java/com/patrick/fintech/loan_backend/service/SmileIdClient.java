package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.SmileIdJobType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Thin HTTP wrapper around Smile ID's REST API.
 *
 * IMPORTANT -- one thing worth confirming yourself once you're in your
 * actual Sandbox dashboard: the exact job-submission endpoint path and
 * payload shape has shifted across Smile ID's API versions in their own
 * docs (some pages show a two-step "get signed S3 URL, then upload a zip"
 * flow; others show images passed inline as base64 in a single call).
 * This implementation uses the inline-base64 approach, which Smile ID's
 * own docs confirm is supported ("images can either be parsed inline as
 * a base64 encoded string or... zipped"). If your Sandbox dashboard's
 * sample code shows a different shape when you get your real partner_id,
 * match that instead -- the signature generation (SmileIdSignatureService)
 * is independently correct regardless of which submission shape you use.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SmileIdClient {

    private final SmileIdSignatureService signatureService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.smileid.partner-id:}")
    private String partnerId;

    @Value("${app.smileid.sandbox:true}")
    private boolean sandbox;

    @Value("${app.smileid.callback-url:}")
    private String callbackUrl;

    private String baseUrl() {
        return sandbox ? "https://testapi.smileidentity.com/v1" : "https://api.smileidentity.com/v1";
    }

    /**
     * Submits a Biometric KYC job: ID document image + selfie image, run
     * through document verification, face match, and liveness detection
     * in one call. Covers steps 1-4 of the pipeline.
     */
    public Map<String, Object> submitBiometricKyc(
            String userId, String jobId,
            String idDocumentBase64, String selfieBase64,
            String country, String idType, String idNumber) {

        SmileIdSignatureService.Signed signed = signatureService.sign();

        Map<String, Object> partnerParams = new HashMap<>();
        partnerParams.put("user_id", userId);
        partnerParams.put("job_id", jobId);
        partnerParams.put("job_type", SmileIdJobType.BIOMETRIC_KYC.code);

        Map<String, Object> idInfo = new HashMap<>();
        idInfo.put("country", country);
        idInfo.put("id_type", idType);
        idInfo.put("id_number", idNumber);

        Map<String, Object> body = new HashMap<>();
        body.put("partner_id", partnerId);
        body.put("timestamp", signed.timestamp());
        body.put("signature", signed.signature());
        body.put("partner_params", partnerParams);
        body.put("id_info", idInfo);
        body.put("images", java.util.List.of(
            Map.of("image_type_id", 3, "image", idDocumentBase64),   // 3 = ID card image (base64)
            Map.of("image_type_id", 2, "image", selfieBase64)        // 2 = selfie image (base64)
        ));
        if (callbackUrl != null && !callbackUrl.isBlank()) body.put("callback_url", callbackUrl);

        return post("/upload", body);
    }

    /**
     * Enhanced KYC: verifies personal details against the ID authority's own
     * database using just the ID number -- no images needed. Good for a
     * fast first check before asking for selfie/document photos.
     */
    public Map<String, Object> enhancedKyc(String country, String idType, String idNumber,
                                            String firstName, String lastName, String dob) {
        SmileIdSignatureService.Signed signed = signatureService.sign();

        Map<String, Object> body = new HashMap<>();
        body.put("partner_id", partnerId);
        body.put("timestamp", signed.timestamp());
        body.put("signature", signed.signature());
        body.put("country", country);
        body.put("id_type", idType);
        body.put("id_number", idNumber);
        if (firstName != null) body.put("first_name", firstName);
        if (lastName != null)  body.put("last_name", lastName);
        if (dob != null)       body.put("dob", dob);
        if (callbackUrl != null && !callbackUrl.isBlank()) body.put("callback_url", callbackUrl);

        return post("/id_verification", body);
    }

    /** AML / PEP / sanctions / adverse-media screening by full name (+ optional DOB/country). */
    public Map<String, Object> amlScreening(String fullName, String dob, String country) {
        SmileIdSignatureService.Signed signed = signatureService.sign();

        Map<String, Object> body = new HashMap<>();
        body.put("partner_id", partnerId);
        body.put("timestamp", signed.timestamp());
        body.put("signature", signed.signature());
        body.put("full_name", fullName);
        if (dob != null) body.put("dob", dob);
        if (country != null) body.put("country", country);

        return post("/aml", body);
    }

    public Map<String, Object> getJobStatus(String userId, String jobId) {
        SmileIdSignatureService.Signed signed = signatureService.sign();

        Map<String, Object> body = new HashMap<>();
        body.put("partner_id", partnerId);
        body.put("timestamp", signed.timestamp());
        body.put("signature", signed.signature());
        body.put("user_id", userId);
        body.put("job_id", jobId);
        body.put("image_links", false);
        body.put("history", false);

        return post("/job_status", body);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, Map<String, Object> body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + path, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Smile ID request to {} failed: {}", path, e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("error", true);
            error.put("message", e.getMessage());
            return error;
        }
    }
}
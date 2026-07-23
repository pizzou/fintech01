package com.patrick.fintech.loan_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Generates the HMAC-SHA256 signature Smile ID requires on every request,
 * exactly matching their published algorithm: HMAC-SHA256, keyed with your
 * API key, over the concatenation of (timestamp + partner_id + "sid_request"),
 * base64-encoded. Smile ID's servers recompute this independently, so it
 * must match byte-for-byte -- string concatenation order and the literal
 * "sid_request" suffix both matter.
 */
@Service
@RequiredArgsConstructor
public class SmileIdSignatureService {

    @Value("${app.smileid.partner-id:}")
    private String partnerId;

    @Value("${app.smileid.api-key:}")
    private String apiKey;

    public record Signed(String timestamp, String signature) {}

    public Signed sign() {
        String timestamp = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneOffset.UTC)
            .format(Instant.now());

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(apiKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
            mac.update(partnerId.getBytes(StandardCharsets.UTF_8));
            mac.update("sid_request".getBytes(StandardCharsets.UTF_8));
            String signature = Base64.getEncoder().encodeToString(mac.doFinal());
            return new Signed(timestamp, signature);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate Smile ID request signature: " + e.getMessage(), e);
        }
    }

    /**
     * Verifies a signature Smile ID sent back to us (on their callback/webhook),
     * confirming the callback genuinely came from them and wasn't forged.
     */
    public boolean verifyIncoming(String timestamp, String receivedSignature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(apiKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
            mac.update(partnerId.getBytes(StandardCharsets.UTF_8));
            mac.update("sid_request".getBytes(StandardCharsets.UTF_8));
            String expected = Base64.getEncoder().encodeToString(mac.doFinal());
            return expected.equals(receivedSignature);
        } catch (Exception e) {
            return false;
        }
    }
}
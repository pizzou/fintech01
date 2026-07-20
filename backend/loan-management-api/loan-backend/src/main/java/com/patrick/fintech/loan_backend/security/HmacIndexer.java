package com.patrick.fintech.loan_backend.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Deterministic HMAC-SHA256 "blind index" for fields that are encrypted
 * (via CryptoConverter, so not directly searchable) but still need exact-
 * match lookups — e.g. finding a returning borrower by phone number.
 *
 * Unlike CryptoConverter's AES-GCM (randomized, different ciphertext every
 * time), the same input always produces the same HMAC output — that's what
 * makes it usable as a lookup key. It does NOT reveal the original value
 * (HMAC is one-way), only whether two values are equal, which is exactly
 * what an indexed lookup needs and no more.
 *
 * Store the plaintext-derived index alongside the encrypted column (e.g.
 * Borrower.phoneHash next to Borrower.phone) and query by the index, never
 * by the encrypted column directly.
 */
public final class HmacIndexer {

    private HmacIndexer() {}

    private static volatile SecretKeySpec KEY;

    public static String index(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) return null;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key());
            byte[] result = mac.doFinal(plaintext.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(result);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute blind index", e);
        }
    }

    private static SecretKeySpec key() {
        if (KEY != null) return KEY;
        synchronized (HmacIndexer.class) {
            if (KEY != null) return KEY;
            String configured = System.getenv("APP_INDEX_KEY");
            byte[] keyBytes;
            if (configured != null && !configured.isBlank()) {
                keyBytes = Base64.getDecoder().decode(configured);
            } else {
                // Deliberately distinct from CryptoConverter's dev key, but equally insecure —
                // set APP_INDEX_KEY (openssl rand -base64 32) before handling real data.
                byte[] seed = Base64.getDecoder().decode("ZGV2LW9ubHktaW5kZXgta2V5LWRvLW5vdC11c2U9");
                keyBytes = new byte[32];
                for (int i = 0; i < 32; i++) keyBytes[i] = seed[i % seed.length];
            }
            KEY = new SecretKeySpec(keyBytes, "HmacSHA256");
            return KEY;
        }
    }
}

package com.patrick.fintech.loan_backend.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Field-level AES-256-GCM encryption for sensitive PII columns (national ID,
 * phone, address, spouse details) — applied via @Convert on the entity field.
 *
 * This encrypts the actual column VALUE, independent of database access
 * controls or disk encryption: even someone with raw read access to the
 * database (a leaked backup, a compromised DB user, a cloud provider
 * incident) sees ciphertext, not the plaintext national ID or phone number.
 *
 * Format stored in the DB: base64( 12-byte random IV || GCM ciphertext+tag ).
 * A fresh random IV is generated on every encryption — the same plaintext
 * encrypted twice produces different ciphertext (this is intentional: it
 * prevents pattern analysis, but means this column can't be searched by
 * exact match. Fields that must be *searchable* — like phone, used to find
 * a returning borrower — pair this with a separate deterministic HMAC blind
 * index column; see HmacIndexer and Borrower.phoneHash.
 *
 * Key: set APP_ENCRYPTION_KEY to a 32-byte key, base64-encoded (openssl rand
 * -base64 32). Without it, a fixed development-only key is used — a loud
 * warning is logged so this can't go unnoticed into production.
 *
 * Note on wiring: JPA AttributeConverters are instantiated by Hibernate via
 * reflection, not by Spring's DI container, so this deliberately reads the
 * key straight from the environment (System.getenv) rather than using
 * @Value — @Value injection into a Hibernate-managed converter is not
 * reliable across Hibernate/Spring Boot version combinations.
 */
@Converter
public class CryptoConverter implements AttributeConverter<String, String> {

    private static final String ALGO = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CryptoConverter.class);

    private static volatile SecretKeySpec KEY;
    private static volatile boolean warnedInsecureKey = false;

    private SecretKeySpec key() {
        if (KEY != null) return KEY;
        synchronized (CryptoConverter.class) {
            if (KEY != null) return KEY;
            String configuredKey = System.getenv("APP_ENCRYPTION_KEY");
            byte[] keyBytes;
            if (configuredKey != null && !configuredKey.isBlank()) {
                keyBytes = Base64.getDecoder().decode(configuredKey);
                if (keyBytes.length != 32) {
                    throw new IllegalStateException(
                        "APP_ENCRYPTION_KEY must decode to exactly 32 bytes (AES-256) — got " + keyBytes.length);
                }
            } else {
                if (!warnedInsecureKey) {
                    log.warn("╔══════════════════════════════════════════════════════════════╗");
                    log.warn("║  APP_ENCRYPTION_KEY is not set — using an INSECURE DEV-ONLY   ║");
                    log.warn("║  fixed key. PII encrypted with this key is NOT actually       ║");
                    log.warn("║  protected. Set APP_ENCRYPTION_KEY before handling real data. ║");
                    log.warn("║  Generate one with: openssl rand -base64 32                   ║");
                    log.warn("╚══════════════════════════════════════════════════════════════╝");
                    warnedInsecureKey = true;
                }
                // Fixed, publicly-known dev key — deliberately NOT secret, so it's obviously unsafe for real data.
                byte[] seed = Base64.getDecoder().decode("ZGV2LW9ubHktaW5zZWN1cmUta2V5LWRvLW5vdC11c2U9");
                byte[] padded = new byte[32];
                for (int i = 0; i < 32; i++) padded[i] = seed[i % seed.length];
                keyBytes = padded;
            }
            KEY = new SecretKeySpec(keyBytes, "AES");
            return KEY;
        }
    }

    @Override
    public String convertToDatabaseColumn(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) return plaintext;
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            SecureRandom.getInstanceStrong().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return "enc:" + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            log.error("PII encryption failed — refusing to store plaintext for a field that should be encrypted", e);
            throw new IllegalStateException("Failed to encrypt sensitive field", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String stored) {
        if (stored == null || stored.isBlank()) return stored;
        // Tolerate pre-existing plaintext rows from before encryption was enabled, rather than
        // crashing every read — they'll be re-encrypted automatically the next time they're saved.
        if (!stored.startsWith("enc:")) return stored;
        try {
            byte[] combined = Base64.getDecoder().decode(stored.substring(4));
            byte[] iv = new byte[IV_LENGTH_BYTES];
            byte[] ciphertext = new byte[combined.length - IV_LENGTH_BYTES];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH_BYTES);
            System.arraycopy(combined, IV_LENGTH_BYTES, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("PII decryption failed for a stored value — wrong key, or corrupted data", e);
            throw new IllegalStateException("Failed to decrypt sensitive field", e);
        }
    }
}

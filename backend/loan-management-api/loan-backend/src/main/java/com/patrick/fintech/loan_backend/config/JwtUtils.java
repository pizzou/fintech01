package com.patrick.fintech.loan_backend.config;

import com.patrick.fintech.loan_backend.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
@Slf4j
public class JwtUtils {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-ms:86400000}")
    private long expirationMs;

    private SecretKey getSigningKey() {

        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "app.jwt.secret is not configured"
            );
        }

        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least 32 bytes long"
            );
        }

        return Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * ============================================================
     * TENANT-AWARE LOGIN TOKEN
     * ============================================================
     *
     * This is the method that normal user login MUST use.
     *
     * The JWT contains:
     *
     * - email
     * - userId
     * - organizationId
     * - role
     *
     * Example:
     *
     * {
     *   "sub": "admin@growthfinance.rw",
     *   "userId": 12,
     *   "organizationId": 1,
     *   "role": "ADMIN"
     * }
     *
     * This prevents a token belonging to Growth Finance from
     * being treated as a Noble Loan Solutions token.
     */
    public String generateToken(User user) {

        if (user == null) {
            throw new IllegalArgumentException(
                    "Cannot generate JWT for null user"
            );
        }

        Long organizationId = null;

        /*
         * SUPER_ADMIN is platform-level and intentionally has
         * no organization.
         */
        if (user.getOrganization() != null) {
            organizationId = user.getOrganization().getId();
        }

        String role = null;

        if (user.getRole() != null) {
            role = user.getRole().getName();
        }

        Date issuedAt = new Date();

        Date expiration = new Date(
                issuedAt.getTime() + expirationMs
        );

        return Jwts.builder()
                .subject(user.getEmail())

                .claim("userId", user.getId())

                .claim("organizationId", organizationId)

                .claim("role", role)

                .issuedAt(issuedAt)

                .expiration(expiration)

                .signWith(getSigningKey())

                .compact();
    }

    /**
     * ============================================================
     * BACKWARD COMPATIBILITY METHOD
     * ============================================================
     *
     * Keep this temporarily because other parts of your application
     * may still call:
     *
     * jwtUtils.generateToken(email)
     *
     * IMPORTANT:
     *
     * Do NOT use this method for normal tenant login.
     *
     * Normal login must call:
     *
     * jwtUtils.generateToken(user)
     */
    public String generateToken(String email) {

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException(
                    "Email cannot be empty"
            );
        }

        Date issuedAt = new Date();

        Date expiration = new Date(
                issuedAt.getTime() + expirationMs
        );

        return Jwts.builder()
                .subject(email.trim().toLowerCase())
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * ============================================================
     * MFA SETUP TOKEN
     * ============================================================
     *
     * This is NOT a normal authentication token.
     *
     * It is only used during MFA enrollment.
     */
    public String generateSetupToken(String email) {

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException(
                    "Email cannot be empty"
            );
        }

        Date issuedAt = new Date();

        Date expiration = new Date(
                issuedAt.getTime() + 10 * 60 * 1000
        );

        return Jwts.builder()
                .subject(email.trim().toLowerCase())

                .claim("purpose", "mfa-setup")

                .issuedAt(issuedAt)

                .expiration(expiration)

                .signWith(getSigningKey())

                .compact();
    }

    /**
     * Check whether a token is specifically an MFA setup token.
     */
    public boolean isSetupToken(String token) {

        try {

            Claims claims = parseClaims(token);

            Object purpose = claims.get("purpose");

            return "mfa-setup".equals(purpose);

        } catch (JwtException | IllegalArgumentException e) {

            return false;
        }
    }

    /**
     * ============================================================
     * EMAIL
     * ============================================================
     */
    public String getEmailFromToken(String token) {

        return parseClaims(token).getSubject();
    }

    /**
     * ============================================================
     * USER ID
     * ============================================================
     */
    public Long getUserIdFromToken(String token) {

        Object value = parseClaims(token).get("userId");

        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.longValue();
        }

        return Long.valueOf(value.toString());
    }

    /**
     * ============================================================
     * ORGANIZATION ID
     * ============================================================
     *
     * This is the critical tenant claim.
     */
    public Long getOrganizationIdFromToken(String token) {

        Object value = parseClaims(token).get("organizationId");

        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.longValue();
        }

        return Long.valueOf(value.toString());
    }

    /**
     * ============================================================
     * ROLE
     * ============================================================
     */
    public String getRoleFromToken(String token) {

        Object value = parseClaims(token).get("role");

        return value != null
                ? value.toString()
                : null;
    }

    /**
     * ============================================================
     * CLAIM PARSER
     * ============================================================
     */
    private Claims parseClaims(String token) {

        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException(
                    "JWT token is empty"
            );
        }

        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * ============================================================
     * VALIDATE TOKEN
     * ============================================================
     */
    public boolean validateToken(String token) {

        try {

            parseClaims(token);

            return true;

        } catch (JwtException | IllegalArgumentException e) {

            log.warn(
                    "Invalid JWT token: {}",
                    e.getMessage()
            );

            return false;
        }
    }

    /**
     * ============================================================
     * EXPIRED TOKEN CHECK
     * ============================================================
     */
    public boolean isTokenExpired(String token) {

        try {

            Date expiration =
                    parseClaims(token).getExpiration();

            return expiration.before(new Date());

        } catch (JwtException | IllegalArgumentException e) {

            return true;
        }
    }
}
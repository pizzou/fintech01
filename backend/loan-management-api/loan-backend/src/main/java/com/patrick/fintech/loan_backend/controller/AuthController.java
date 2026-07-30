package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.config.JwtUtils;
import com.patrick.fintech.loan_backend.dto.LoginRequest;
import com.patrick.fintech.loan_backend.dto.RegisterRequest;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.UserRepository;
import com.patrick.fintech.loan_backend.security.TenantContext;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.service.AuthService;
import com.patrick.fintech.loan_backend.service.MailService;
import com.patrick.fintech.loan_backend.service.MfaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;
    private final MfaService mfaService;
    private final MailService mailService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    private static final SecureRandom OTP_RANDOM =
            new SecureRandom();

    private static final Set<String> MFA_MANDATORY_ROLES =
            Set.of("ADMIN", "MANAGER");

    private static final int MAX_FAILED_ATTEMPTS = 5;

    private static final int LOCKOUT_MINUTES = 15;

    @PostMapping("/register")
    @Transactional
    public ResponseEntity<Map<String, Object>> register(
            @RequestBody RegisterRequest req
    ) {

        User created =
                authService.register(req);

        auditService.log(
                created.getOrganization(),
                created,
                "USER_REGISTERED",
                "AUTH",
                String.valueOf(created.getId()),
                created.getName()
                        + " ("
                        + created.getEmail()
                        + ") registered",
                null,
                null,
                "Authentication"
        );

        return ResponseEntity.ok(
                safe(created)
        );
    }

    @PostMapping("/login")
    @Transactional
    public ResponseEntity<Map<String, Object>> login(
            @RequestBody LoginRequest req
    ) {

        /*
         * ==========================================================
         * 1. TENANT MUST EXIST
         * ==========================================================
         */

        Organization requestOrg =
                TenantContext.get();

        /*
         * If this is a tenant login page, there MUST be a tenant.
         *
         * This prevents:
         *
         * POST /api/auth/login
         *
         * from becoming a global login endpoint.
         */
        if (requestOrg == null) {

            throw new RuntimeException(
                    "Tenant could not be determined for this login request."
            );
        }

        /*
         * ==========================================================
         * 2. NORMALIZE EMAIL
         * ==========================================================
         */

        if (req.getEmail() == null
                || req.getEmail().isBlank()) {

            throw new RuntimeException(
                    "Invalid email or password"
            );
        }

        String email =
                req.getEmail()
                        .trim()
                        .toLowerCase();

        /*
         * ==========================================================
         * 3. FIND USER INSIDE THIS TENANT ONLY
         * ==========================================================
         *
         * THIS IS THE CRITICAL FIX.
         */

        User user =
                userRepository
                        .findByOrganizationAndEmail(
                                requestOrg,
                                email
                        )
                        .orElse(null);

        /*
         * If Growth Finance user tries to log in from Noble:
         *
         * requestOrg = Noble
         *
         * findByOrganizationAndEmail(Noble, growth@email.com)
         *
         * => empty
         *
         * Therefore authentication fails.
         */

        if (user == null) {

            auditService.log(
                    requestOrg,
                    null,
                    "LOGIN_FAILED",
                    "AUTH",
                    null,
                    "Login rejected — invalid tenant credentials",
                    null,
                    null,
                    "Authentication"
            );

            throw new RuntimeException(
                    "Invalid email or password"
            );
        }

        /*
         * ==========================================================
         * 4. VERIFY USER REALLY BELONGS TO TENANT
         * ==========================================================
         */

        if (user.getOrganization() == null
                || !requestOrg.getId()
                    .equals(user.getOrganization().getId())) {

            throw new RuntimeException(
                    "Invalid email or password"
            );
        }

        /*
         * ==========================================================
         * 5. ACCOUNT LOCK CHECK
         * ==========================================================
         */

        LocalDateTime now =
                LocalDateTime.now();

        if (user.getLockedUntil() != null
                && user.getLockedUntil().isAfter(now)) {

            long minutesLeft =
                    Duration.between(
                            now,
                            user.getLockedUntil()
                    ).toMinutes() + 1;

            auditService.log(
                    user.getOrganization(),
                    user,
                    "LOGIN_BLOCKED_ACCOUNT_LOCKED",
                    "AUTH",
                    String.valueOf(user.getId()),
                    "Login attempt rejected — account locked",
                    null,
                    null,
                    "Authentication"
            );

            throw new RuntimeException(
                    "Account locked due to repeated failed logins. "
                            + "Try again in "
                            + minutesLeft
                            + " minute(s)."
            );
        }

        /*
         * ==========================================================
         * 6. VERIFY PASSWORD
         * ==========================================================
         */

        boolean passwordCorrect =
                req.getPassword() != null
                        && passwordEncoder.matches(
                                req.getPassword(),
                                user.getPassword()
                        );

        if (!passwordCorrect) {

            int attempts =
                    user.getFailedLoginAttempts() == null
                            ? 0
                            : user.getFailedLoginAttempts();

            attempts++;

            user.setFailedLoginAttempts(
                    attempts
            );

            if (attempts >= MAX_FAILED_ATTEMPTS) {

                user.setLockedUntil(
                        now.plusMinutes(
                                LOCKOUT_MINUTES
                        )
                );

                userRepository.save(user);

                auditService.log(
                        user.getOrganization(),
                        user,
                        "ACCOUNT_LOCKED",
                        "AUTH",
                        String.valueOf(user.getId()),
                        "Account locked after "
                                + attempts
                                + " failed login attempts",
                        null,
                        null,
                        "Authentication"
                );

                throw new RuntimeException(
                        "Too many failed attempts. "
                                + "Account locked for "
                                + LOCKOUT_MINUTES
                                + " minutes."
                );
            }

            userRepository.save(user);

            auditService.log(
                    user.getOrganization(),
                    user,
                    "LOGIN_FAILED",
                    "AUTH",
                    String.valueOf(user.getId()),
                    "Failed login attempt ("
                            + attempts
                            + "/"
                            + MAX_FAILED_ATTEMPTS
                            + ")",
                    null,
                    null,
                    "Authentication"
            );

            throw new RuntimeException(
                    "Invalid email or password"
            );
        }

        /*
         * ==========================================================
         * 7. PASSWORD CORRECT
         * ==========================================================
         */

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);

        userRepository.save(user);

        /*
         * ==========================================================
         * 8. MFA
         * ==========================================================
         */

        boolean mfaRequiredForRole =
                user.getRole() != null
                        && MFA_MANDATORY_ROLES.contains(
                                user.getRole().getName()
                        );

        if (!user.isTwoFactorEnabled()
                && mfaRequiredForRole) {

            Map<String, Object> body =
                    new LinkedHashMap<>();

            body.put(
                    "mfaSetupRequired",
                    true
            );

            body.put(
                    "mfaRequired",
                    false
            );

            body.put(
                    "email",
                    user.getEmail()
            );

            body.put(
                    "setupToken",
                    jwtUtils.generateSetupToken(
                            user.getEmail()
                    )
            );

            body.put(
                    "message",
                    "Your role requires two-factor authentication. Complete setup to continue."
            );

            return ResponseEntity.ok(body);
        }

        /*
         * ==========================================================
         * 9. TOTP MFA
         * ==========================================================
         */

        if (user.isTwoFactorEnabled()) {

            if (req.getMfaCode() == null
                    || req.getMfaCode().isBlank()) {

                return ResponseEntity.ok(
                        Map.of(
                                "mfaRequired",
                                true,

                                "email",
                                user.getEmail()
                        )
                );
            }

            if (!mfaService.verifyCode(
                    user,
                    req.getMfaCode()
            )) {

                throw new RuntimeException(
                        "Invalid MFA code"
                );
            }

        } else {

            /*
             * ======================================================
             * 10. EMAIL OTP
             * ======================================================
             */

            if (req.getOtp() == null
                    || req.getOtp().isBlank()) {

                String code =
                        String.format(
                                "%06d",
                                OTP_RANDOM.nextInt(
                                        1_000_000
                                )
                        );

                user.setLoginOtpHash(
                        passwordEncoder.encode(code)
                );

                user.setLoginOtpExpiresAt(
                        now.plusMinutes(5)
                );

                user.setLoginOtpAttempts(0);

                userRepository.save(user);

                mailService.sendLoginOtp(
                        user,
                        code
                );

                return ResponseEntity.ok(
                        Map.of(
                                "otpRequired",
                                true,

                                "email",
                                user.getEmail(),

                                "message",
                                "We sent a 6-digit verification code to your email."
                        )
                );
            }

            if (user.getLoginOtpHash() == null
                    || user.getLoginOtpExpiresAt() == null
                    || user.getLoginOtpExpiresAt()
                        .isBefore(now)) {

                throw new RuntimeException(
                        "Your verification code has expired. "
                                + "Please sign in again to get a new one."
                );
            }

            int otpAttempts =
                    user.getLoginOtpAttempts() == null
                            ? 0
                            : user.getLoginOtpAttempts();

            if (otpAttempts >= 5) {

                user.setLoginOtpHash(null);
                user.setLoginOtpExpiresAt(null);
                user.setLoginOtpAttempts(0);

                userRepository.save(user);

                throw new RuntimeException(
                        "Too many incorrect codes. "
                                + "Please sign in again to get a new one."
                );
            }

            if (!passwordEncoder.matches(
                    req.getOtp().trim(),
                    user.getLoginOtpHash()
            )) {

                user.setLoginOtpAttempts(
                        otpAttempts + 1
                );

                userRepository.save(user);

                throw new RuntimeException(
                        "Incorrect verification code."
                );
            }

            /*
             * Consume OTP.
             */
            user.setLoginOtpHash(null);
            user.setLoginOtpExpiresAt(null);
            user.setLoginOtpAttempts(0);

            userRepository.save(user);
        }

        /*
         * ==========================================================
         * 11. SUCCESS
         * ==========================================================
         */

        auditService.log(
                user.getOrganization(),
                user,
                "LOGIN_SUCCESS",
                "AUTH",
                String.valueOf(user.getId()),
                user.getName()
                        + " signed in to "
                        + requestOrg.getName(),
                null,
                null,
                "Authentication"
        );

        /*
         * Generate JWT only after tenant-scoped authentication.
         */
        String token =
                jwtUtils.generateToken(user);

        Map<String, Object> body =
                safe(user);

        body.put(
                "token",
                token
        );

        body.put(
                "mfaRequired",
                false
        );

        body.put(
                "mfaSetupRequired",
                false
        );

        body.put(
                "otpRequired",
                false
        );

        return ResponseEntity.ok(body);
    }

    @GetMapping("/me")
    @Transactional
    public ResponseEntity<Map<String, Object>> me(
            org.springframework.security.core.Authentication auth
    ) {

        Organization requestOrg =
                TenantContext.get();

        if (requestOrg == null) {

            throw new RuntimeException(
                    "Tenant could not be determined."
            );
        }

        String email =
                auth.getName()
                        .trim()
                        .toLowerCase();

        /*
         * Again: tenant-scoped lookup.
         */
        User user =
                userRepository
                        .findByOrganizationAndEmail(
                                requestOrg,
                                email
                        )
                        .orElseThrow(
                                () -> new RuntimeException(
                                        "Current user not found."
                                )
                        );

        /*
         * Extra protection.
         */
        if (user.getOrganization() == null
                || !requestOrg.getId()
                    .equals(user.getOrganization().getId())) {

            throw new RuntimeException(
                    "Tenant mismatch."
            );
        }

        return ResponseEntity.ok(
                safe(user)
        );
    }

    private Map<String, Object> safe(User u) {

        Map<String, Object> m =
                new LinkedHashMap<>();

        m.put(
                "userId",
                u.getId()
        );

        m.put(
                "name",
                u.getName()
        );

        m.put(
                "email",
                u.getEmail()
        );

        m.put(
                "role",
                u.getRole() != null
                        ? u.getRole().getName()
                        : null
        );

        m.put(
                "twoFactorEnabled",
                u.isTwoFactorEnabled()
        );

        m.put(
                "mustChangePassword",
                u.isMustChangePassword()
        );

        if (u.getOrganization() != null) {

            m.put(
                    "organizationId",
                    u.getOrganization().getId()
            );

            m.put(
                    "organizationName",
                    u.getOrganization().getName()
            );

            m.put(
                    "currency",
                    u.getOrganization()
                            .getDefaultCurrency()
            );

            m.put(
                    "locale",
                    u.getOrganization()
                            .getLocale()
            );

            m.put(
                    "timezone",
                    u.getOrganization()
                            .getTimezone()
            );

        } else {

            m.put(
                    "organizationId",
                    null
            );

            m.put(
                    "organizationName",
                    null
            );

            m.put(
                    "currency",
                    "USD"
            );

            m.put(
                    "locale",
                    "en-US"
            );

            m.put(
                    "timezone",
                    "UTC"
            );
        }

        return m;
    }
}
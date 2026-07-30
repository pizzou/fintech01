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
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
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

    private final AuthenticationManager authenticationManager;
    private final AuthService authService;
    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;
    private final MfaService mfaService;
    private final MailService mailService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    private static final SecureRandom OTP_RANDOM = new SecureRandom();

    private static final Set<String> MFA_MANDATORY_ROLES =
            Set.of("ADMIN", "MANAGER");

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 15;

    /*
     * ============================================================
     * REGISTER
     * ============================================================
     */

    @PostMapping("/register")
    @Transactional
    public ResponseEntity<Map<String, Object>> register(
            @RequestBody RegisterRequest req
    ) {

        User created = authService.register(req);

        auditService.log(
                created.getOrganization(),
                created,
                "USER_REGISTERED",
                "AUTH",
                String.valueOf(created.getId()),
                created.getName() + " (" + created.getEmail() + ") registered",
                null,
                null,
                "Authentication"
        );

        return ResponseEntity.ok(safe(created));
    }


    /*
     * ============================================================
     * LOGIN
     * ============================================================
     *
     * IMPORTANT:
     *
     * Tenant is resolved FIRST from TenantContext.
     *
     * Then the user is looked up INSIDE that organization.
     *
     * Therefore:
     *
     * Growth Finance user
     * +
     * Noble Loan Solutions domain
     *
     * => user is NOT authenticated.
     *
     * This prevents cross-tenant login.
     */

    @PostMapping("/login")
    @Transactional
    public ResponseEntity<Map<String, Object>> login(
            @RequestBody LoginRequest req
    ) {

        /*
         * --------------------------------------------------------
         * Validate request
         * --------------------------------------------------------
         */

        if (req == null) {
            throw new RuntimeException("Invalid login request");
        }

        if (req.getEmail() == null || req.getEmail().isBlank()) {
            throw new RuntimeException("Invalid email or password");
        }

        if (req.getPassword() == null || req.getPassword().isBlank()) {
            throw new RuntimeException("Invalid email or password");
        }

        String email = req.getEmail().trim().toLowerCase();


        /*
         * --------------------------------------------------------
         * RESOLVE TENANT BEFORE USER AUTHENTICATION
         * --------------------------------------------------------
         */

        Organization requestOrg = TenantContext.get();


        /*
         * If there is no tenant, do NOT perform a normal customer
         * login.
         *
         * This prevents someone from calling:
         *
         * POST /api/auth/login
         *
         * directly against the Render backend and bypassing the
         * customer-domain requirement.
         *
         * Platform SUPER_ADMIN can be handled separately if your
         * platform needs that functionality.
         */

        if (requestOrg == null) {

            throw new RuntimeException(
                    "Invalid tenant. Please access your organization's official website."
            );
        }


        /*
         * --------------------------------------------------------
         * TENANT-SCOPED USER LOOKUP
         * --------------------------------------------------------
         *
         * DO NOT use:
         *
         * userRepository.findByEmail(email)
         *
         * here.
         *
         * That performs a global lookup.
         *
         * Instead:
         *
         * findByEmailAndOrganization()
         */

        User user = userRepository
                .findByEmailAndOrganization(email, requestOrg)
                .orElse(null);


        /*
         * --------------------------------------------------------
         * USER DOES NOT BELONG TO THIS TENANT
         * --------------------------------------------------------
         *
         * Return the same generic message as bad credentials.
         *
         * Do not reveal:
         *
         * "This email belongs to Growth Finance."
         *
         * while the user is visiting Noble Loan Solutions.
         */

        if (user == null) {

            auditService.log(
                    requestOrg,
                    null,
                    "LOGIN_FAILED",
                    "AUTH",
                    null,
                    "Login failed — invalid credentials for tenant "
                            + requestOrg.getName(),
                    null,
                    null,
                    "Authentication"
            );

            throw new RuntimeException(
                    "Invalid email or password"
            );
        }


        /*
         * --------------------------------------------------------
         * ACCOUNT LOCK CHECK
         * --------------------------------------------------------
         */

        if (
                user.getLockedUntil() != null
                        && user.getLockedUntil().isAfter(LocalDateTime.now())
        ) {

            long minutesLeft =
                    Duration.between(
                            LocalDateTime.now(),
                            user.getLockedUntil()
                    ).toMinutes() + 1;

            auditService.log(
                    requestOrg,
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
         * --------------------------------------------------------
         * PASSWORD AUTHENTICATION
         * --------------------------------------------------------
         *
         * AuthenticationManager may still use the email globally.
         * That is okay for password verification because we have
         * ALREADY established that this email belongs to the
         * current tenant.
         *
         * The critical security boundary is the tenant-scoped
         * lookup above.
         */

        try {

            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            email,
                            req.getPassword()
                    )
            );

        } catch (Exception e) {

            int attempts =
                    user.getFailedLoginAttempts() == null
                            ? 0
                            : user.getFailedLoginAttempts();

            attempts++;

            user.setFailedLoginAttempts(attempts);


            /*
             * ----------------------------------------------------
             * LOCK ACCOUNT AFTER TOO MANY ATTEMPTS
             * ----------------------------------------------------
             */

            if (attempts >= MAX_FAILED_ATTEMPTS) {

                user.setLockedUntil(
                        LocalDateTime.now()
                                .plusMinutes(LOCKOUT_MINUTES)
                );

                userRepository.save(user);

                auditService.log(
                        requestOrg,
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
                        "Too many failed attempts. Account locked for "
                                + LOCKOUT_MINUTES
                                + " minutes."
                );
            }


            userRepository.save(user);

            auditService.log(
                    requestOrg,
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
         * --------------------------------------------------------
         * IMPORTANT SECOND TENANT CHECK
         * --------------------------------------------------------
         *
         * This is defensive.
         *
         * The user was already retrieved using:
         *
         * email + organization
         *
         * But we verify the relationship again before issuing
         * anything.
         */

        if (
                user.getOrganization() == null
                        || !requestOrg.getId()
                        .equals(user.getOrganization().getId())
        ) {

            auditService.log(
                    requestOrg,
                    user,
                    "LOGIN_BLOCKED_WRONG_TENANT",
                    "AUTH",
                    String.valueOf(user.getId()),
                    "Login rejected — authenticated user does not belong "
                            + "to the requested tenant",
                    null,
                    null,
                    "Authentication"
            );

            throw new RuntimeException(
                    "Invalid email or password"
            );
        }


        /*
         * --------------------------------------------------------
         * SUCCESSFUL PASSWORD CHECK
         * --------------------------------------------------------
         */

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(LocalDateTime.now());

        userRepository.save(user);


        /*
         * --------------------------------------------------------
         * MFA REQUIREMENT
         * --------------------------------------------------------
         */

        boolean mfaRequiredForRole =
                user.getRole() != null
                        && MFA_MANDATORY_ROLES.contains(
                                user.getRole().getName()
                        );


        /*
         * --------------------------------------------------------
         * MANDATORY MFA SETUP
         * --------------------------------------------------------
         */

        if (
                !user.isTwoFactorEnabled()
                        && mfaRequiredForRole
        ) {

            Map<String, Object> body =
                    new LinkedHashMap<>();

            body.put("mfaSetupRequired", true);
            body.put("mfaRequired", false);
            body.put("otpRequired", false);
            body.put("email", user.getEmail());

            body.put(
                    "setupToken",
                    jwtUtils.generateSetupToken(
                            user.getEmail()
                    )
            );

            body.put(
                    "message",
                    "Your role requires two-factor authentication. "
                            + "Complete setup to continue."
            );

            return ResponseEntity.ok(body);
        }


        /*
         * --------------------------------------------------------
         * TOTP MFA
         * --------------------------------------------------------
         */

        if (user.isTwoFactorEnabled()) {

            if (
                    req.getMfaCode() == null
                            || req.getMfaCode().isBlank()
            ) {

                return ResponseEntity.ok(
                        Map.of(
                                "mfaRequired",
                                true,
                                "otpRequired",
                                false,
                                "email",
                                user.getEmail()
                        )
                );
            }


            if (
                    !mfaService.verifyCode(
                            user,
                            req.getMfaCode().trim()
                    )
            ) {

                auditService.log(
                        requestOrg,
                        user,
                        "LOGIN_FAILED_MFA",
                        "AUTH",
                        String.valueOf(user.getId()),
                        "Invalid MFA code",
                        null,
                        null,
                        "Authentication"
                );

                throw new RuntimeException(
                        "Invalid MFA code"
                );
            }

        } else {


            /*
             * ----------------------------------------------------
             * EMAIL OTP
             * ----------------------------------------------------
             *
             * Non-MFA users still receive an email verification
             * code.
             */

            LocalDateTime now =
                    LocalDateTime.now();


            /*
             * Generate OTP when one wasn't supplied.
             */

            if (
                    req.getOtp() == null
                            || req.getOtp().isBlank()
            ) {

                String code =
                        String.format(
                                "%06d",
                                OTP_RANDOM.nextInt(1_000_000)
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
                                "mfaRequired",
                                false,
                                "email",
                                user.getEmail(),
                                "message",
                                "We sent a 6-digit verification code to your email."
                        )
                );
            }


            /*
             * ----------------------------------------------------
             * VERIFY OTP
             * ----------------------------------------------------
             */

            if (
                    user.getLoginOtpHash() == null
                            || user.getLoginOtpExpiresAt() == null
                            || user.getLoginOtpExpiresAt()
                            .isBefore(now)
            ) {

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


            if (
                    !passwordEncoder.matches(
                            req.getOtp().trim(),
                            user.getLoginOtpHash()
                    )
            ) {

                user.setLoginOtpAttempts(
                        otpAttempts + 1
                );

                userRepository.save(user);

                throw new RuntimeException(
                        "Incorrect verification code."
                );
            }


            /*
             * OTP correct.
             *
             * Consume it so it cannot be reused.
             */

            user.setLoginOtpHash(null);
            user.setLoginOtpExpiresAt(null);
            user.setLoginOtpAttempts(0);

            userRepository.save(user);
        }


        /*
         * --------------------------------------------------------
         * LOGIN SUCCESS
         * --------------------------------------------------------
         */

        auditService.log(
                requestOrg,
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
         * --------------------------------------------------------
         * GENERATE JWT
         * --------------------------------------------------------
         */

        String token =
                jwtUtils.generateToken(
                        user.getEmail()
                );


        /*
         * --------------------------------------------------------
         * RESPONSE
         * --------------------------------------------------------
         */

        Map<String, Object> body =
                safe(user);

        body.put("token", token);
        body.put("mfaRequired", false);
        body.put("mfaSetupRequired", false);
        body.put("otpRequired", false);

        return ResponseEntity.ok(body);
    }


    /*
     * ============================================================
     * CURRENT USER
     * ============================================================
     */

    @GetMapping("/me")
    @Transactional
    public ResponseEntity<Map<String, Object>> me(
            Authentication auth
    ) {

        if (auth == null || !auth.isAuthenticated()) {
            throw new RuntimeException("Not authenticated");
        }


        Organization requestOrg =
                TenantContext.get();


        if (requestOrg == null) {
            throw new RuntimeException(
                    "Tenant could not be resolved"
            );
        }


        String email =
                auth.getName()
                        .trim()
                        .toLowerCase();


        /*
         * IMPORTANT:
         *
         * /me must also be tenant-scoped.
         *
         * Never do:
         *
         * userRepository.findByEmail(email)
         */

        User user =
                userRepository
                        .findByEmailAndOrganization(
                                email,
                                requestOrg
                        )
                        .orElseThrow(
                                () -> new RuntimeException(
                                        "Current user not found"
                                )
                        );


        /*
         * Defensive tenant verification.
         */

        if (
                user.getOrganization() == null
                        || !requestOrg.getId()
                        .equals(user.getOrganization().getId())
        ) {

            throw new RuntimeException(
                    "Current user not found"
            );
        }


        return ResponseEntity.ok(
                safe(user)
        );
    }


    /*
     * ============================================================
     * SAFE USER RESPONSE
     * ============================================================
     */

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


        /*
         * --------------------------------------------------------
         * ORGANIZATION
         * --------------------------------------------------------
         */

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

            /*
             * Platform-level user.
             */

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

package com.patrick.fintech.loan_backend.dto;

import lombok.Data;

@Data
public class LoginRequest {

    private String email;

    private String password;

    private String mfaCode;

    private String otp;

    /*
     * Optional fallback.
     *
     * The preferred tenant identifier is X-Tenant-Key.
     */
    private String tenantKey;
}
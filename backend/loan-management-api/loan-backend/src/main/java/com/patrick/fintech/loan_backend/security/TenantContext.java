package com.patrick.fintech.loan_backend.security;

import com.patrick.fintech.loan_backend.model.Organization;

/**
 * Holds the Organization resolved from the current request's Host header
 * (see TenantResolutionFilter) for the lifetime of that request only.
 *
 * This is deliberately separate from JWT-based auth: staff/customer login
 * still authenticates a User and trusts that User's own organization_id —
 * this ThreadLocal only exists so the anonymous /api/public/** endpoints
 * (apply, track, contact, KYC upload) know which org's public website is
 * being visited, without the frontend having to tell them.
 */
public final class TenantContext {

    private static final ThreadLocal<Organization> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(Organization organization) {
        CURRENT.set(organization);
    }

    public static Organization get() {
        return CURRENT.get();
    }

    /** Must be called at the end of every request (in a finally block) to avoid leaking across pooled threads. */
    public static void clear() {
        CURRENT.remove();
    }
}
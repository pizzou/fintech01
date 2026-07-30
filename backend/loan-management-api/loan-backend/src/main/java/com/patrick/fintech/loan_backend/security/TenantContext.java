package com.patrick.fintech.loan_backend.security;

import com.patrick.fintech.loan_backend.model.Organization;

public final class TenantContext {

    private static final ThreadLocal<Organization> CURRENT_TENANT =
            new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(Organization organization) {
        CURRENT_TENANT.set(organization);
    }

    public static Organization get() {
        return CURRENT_TENANT.get();
    }

    public static boolean exists() {
        return CURRENT_TENANT.get() != null;
    }

    public static Long getOrganizationId() {

        Organization organization = CURRENT_TENANT.get();

        return organization != null
                ? organization.getId()
                : null;
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
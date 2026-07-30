package com.patrick.fintech.loan_backend.util;

import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.UserRepository;
import com.patrick.fintech.loan_backend.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CurrentUserUtil {

    private final UserRepository userRepository;

    public User getCurrentUser() {

        Authentication auth =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (auth == null
                || !auth.isAuthenticated()) {

            throw new RuntimeException(
                    "No authenticated user."
            );
        }

        Organization tenant =
                TenantContext.get();

        if (tenant == null) {

            throw new RuntimeException(
                    "Tenant context is missing."
            );
        }

        String email =
                auth.getName()
                        .trim()
                        .toLowerCase();

        User user =
                userRepository
                        .findByOrganizationAndEmail(
                                tenant,
                                email
                        )
                        .orElseThrow(
                                () -> new RuntimeException(
                                        "Current user does not belong to this organization."
                                )
                        );

        /*
         * Defense in depth.
         */
        if (user.getOrganization() == null
                || !tenant.getId()
                    .equals(user.getOrganization().getId())) {

            throw new RuntimeException(
                    "Tenant mismatch."
            );
        }

        return user;
    }

    public Long getCurrentOrganizationId() {

        return getCurrentUser()
                .getOrganization()
                .getId();
    }

    public Long getCurrentUserId() {

        return getCurrentUser()
                .getId();
    }
}
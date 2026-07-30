
package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.UserRepository;
import com.patrick.fintech.loan_backend.security.TenantContext;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(
            UserRepository userRepository
    ) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        if (email == null || email.isBlank()) {
            throw new UsernameNotFoundException(
                    "Invalid credentials"
            );
        }

        String normalizedEmail =
                email.trim().toLowerCase();

        /*
         * ========================================================
         * RESOLVE CURRENT TENANT
         * ========================================================
         *
         * TenantResolutionFilter must execute before Spring
         * Security authentication.
         *
         * Therefore TenantContext should already contain the
         * organization associated with the customer's domain.
         */

        Organization tenant =
                TenantContext.get();


        /*
         * ========================================================
         * CUSTOMER TENANT LOGIN
         * ========================================================
         */

        if (tenant != null) {

            User user =
                    userRepository
                            .findByEmailAndOrganization(
                                    normalizedEmail,
                                    tenant
                            )
                            .orElseThrow(
                                    () -> new UsernameNotFoundException(
                                            "Invalid credentials"
                                    )
                            );


            /*
             * Defensive check.
             *
             * Even though the repository query is already scoped
             * by organization, verify the relationship before
             * returning UserDetails.
             */

            if (
                    user.getOrganization() == null
                            || !tenant.getId()
                            .equals(
                                    user.getOrganization().getId()
                            )
            ) {

                throw new UsernameNotFoundException(
                        "Invalid credentials"
                );
            }


            return buildUserDetails(user);
        }


        /*
         * ========================================================
         * NO TENANT
         * ========================================================
         *
         * A normal customer must NEVER authenticate without a
         * resolved tenant.
         *
         * This prevents:
         *
         * POST /api/auth/login
         *
         * directly against:
         *
         * fintech01.onrender.com
         *
         * from becoming a way to bypass tenant isolation.
         *
         * Platform SUPER_ADMIN accounts can be handled through
         * a separate platform authentication flow if required.
         */

        throw new UsernameNotFoundException(
                "Invalid tenant"
        );
    }


    /*
     * ============================================================
     * BUILD SPRING SECURITY USER
     * ============================================================
     */

    private UserDetails buildUserDetails(User user) {

        String roleName =
                user.getRole() != null
                        ? user.getRole().getName()
                        : "BORROWER";


        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),

                /*
                 * enabled
                 */
                user.getStatus() == User.UserStatus.ACTIVE,

                /*
                 * accountNonExpired
                 */
                true,

                /*
                 * credentialsNonExpired
                 */
                true,

                /*
                 * accountNonLocked
                 */
                user.getLockedUntil() == null
                        || user.getLockedUntil()
                        .isBefore(
                                java.time.LocalDateTime.now()
                        ),

                List.of(
                        new SimpleGrantedAuthority(
                                "ROLE_" + roleName
                        )
                )
        );
    }
}

package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;


public interface OrganizationRepository extends JpaRepository<Organization, Long> {
    Optional<Organization> findByRegistrationNumber(String regNumber);
    boolean existsByRegistrationNumber(String regNumber);

    /** Any org claiming this domain, verified or not — used for uniqueness checks during self-service claim. */
    Optional<Organization> findByDomainIgnoreCase(String domain);

    /**
     * The org that has actually PROVEN ownership of this domain. Used by
     * TenantResolutionFilter and SecurityConfig's CORS check — a domain
     * someone typed into their dashboard but hasn't verified via DNS TXT
     * record never resolves traffic or gets a CORS pass through this method.
     */
    Optional<Organization> findByDomainIgnoreCaseAndDomainVerifiedTrue(String domain);
}
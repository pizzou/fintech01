package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;


public interface OrganizationRepository extends JpaRepository<Organization, Long> {
    Optional<Organization> findByRegistrationNumber(String regNumber);
    boolean existsByRegistrationNumber(String regNumber);

    /** Any org claiming this domain, verified or not — used for uniqueness checks during self-service claim. */
    Optional<Organization> findByDomainIgnoreCase(String domain);


    Optional<Organization> findByDomainIgnoreCaseAndDomainVerifiedTrue(String domain);
}
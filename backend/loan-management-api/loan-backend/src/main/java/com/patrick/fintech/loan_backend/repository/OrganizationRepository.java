package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;



public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    Optional<Organization> findByDomainIgnoreCase(String domain);

    Optional<Organization> findBySlugIgnoreCase(String slug);

    List<Organization> findAllByActiveTrue();

    boolean existsByDomainIgnoreCase(String domain);
}
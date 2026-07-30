
package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.CreditBureauCheck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CreditBureauCheckRepository
        extends JpaRepository<CreditBureauCheck, Long> {

    List<CreditBureauCheck>
        findByBorrower_IdAndOrganization_IdOrderByCreatedAtDesc(
            Long borrowerId,
            Long organizationId
        );

    Optional<CreditBureauCheck>
        findFirstByBorrower_IdAndOrganization_IdOrderByCreatedAtDesc(
            Long borrowerId,
            Long organizationId
        );

    boolean existsByExternalReference(String externalReference);

    Optional<CreditBureauCheck>
        findByReference(String reference);

    Optional<CreditBureauCheck>
        findByExternalReference(String externalReference);
}


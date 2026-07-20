package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.ESignatureRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ESignatureRequestRepository extends JpaRepository<ESignatureRequest, Long> {
    Optional<ESignatureRequest> findBySigningToken(String token);
    List<ESignatureRequest> findByLoan_IdOrderByCreatedAtDesc(Long loanId);
    List<ESignatureRequest> findByOrganization_IdAndStatus(Long orgId, ESignatureRequest.SignatureStatus status);
}

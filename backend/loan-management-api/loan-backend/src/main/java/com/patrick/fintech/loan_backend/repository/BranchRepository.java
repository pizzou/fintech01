package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.Branch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


public interface BranchRepository extends JpaRepository<Branch, Long> {
    List<Branch> findByOrganization_Id(Long orgId);
    List<Branch> findByOrganization_IdAndActiveTrue(Long orgId);
}

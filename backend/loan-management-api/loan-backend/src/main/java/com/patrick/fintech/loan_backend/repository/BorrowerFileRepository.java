package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.BorrowerFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


public interface BorrowerFileRepository extends JpaRepository<BorrowerFile, Long> {
    List<BorrowerFile> findByBorrowerId(Long borrowerId);
}
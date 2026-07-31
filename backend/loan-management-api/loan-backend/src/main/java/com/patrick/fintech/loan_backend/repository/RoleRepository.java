package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;


public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByName(String name);
}

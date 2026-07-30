package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;


import java.util.List;
import java.util.Optional;


public interface UserRepository extends JpaRepository<User, Long> {

  
    

    Optional<User> findByOrganizationAndEmail(
            Organization organization,
            String email
    );

   
    Optional<User> findByEmail(String email);

    Optional<User> findByEmailAndOrganization(
            String email,
            Organization organization
    );

    boolean existsByEmail(String email);

    List<User> findByOrganization(Organization organization);

    long countByOrganization(Organization organization);

    boolean existsByRole_NameAndOrganizationIsNull(String roleName);
}
package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.RegisterRequest;
import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository         userRepository;
    private final PasswordEncoder        passwordEncoder;
    private final RoleRepository         roleRepository;
    private final OrganizationRepository organizationRepository;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       RoleRepository roleRepository,
                       OrganizationRepository organizationRepository) {
        this.userRepository         = userRepository;
        this.passwordEncoder        = passwordEncoder;
        this.roleRepository         = roleRepository;
        this.organizationRepository = organizationRepository;
    }

    public User register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered: " + request.getEmail());
        }

        // RoleRepository.findByName now accepts String
        String roleName = request.getRole() != null ? request.getRole() : "LOAN_OFFICER";
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new RuntimeException("Role not found: " + roleName));

        Organization org = organizationRepository.findById(request.getOrganizationId())
                .orElseThrow(() -> new RuntimeException("Organization not found: " + request.getOrganizationId()));

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        com.patrick.fintech.loan_backend.security.PasswordPolicy.validate(request.getPassword());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(role);
        user.setOrganization(org);
        return userRepository.save(user);
    }
}

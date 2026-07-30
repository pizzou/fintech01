package com.patrick.fintech.loan_backend.dto.creditbureau;


import lombok.*;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreditBureauRequest {


    private String nationalId;


    private String firstName;


    private String lastName;


    private String dateOfBirth;


    private String phone;


    private String requestReference;
}

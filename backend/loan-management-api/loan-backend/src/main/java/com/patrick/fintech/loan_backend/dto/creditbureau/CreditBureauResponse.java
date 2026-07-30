package com.patrick.fintech.loan_backend.dto.creditbureau;


import lombok.*;


import java.math.BigDecimal;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreditBureauResponse {


    private String providerRequestId;


    private Integer creditScore;


    private String riskGrade;


    private Integer activeFacilities;


    private Integer delinquentAccounts;


    private BigDecimal totalOutstandingDebt;


    private BigDecimal totalMonthlyObligations;


    private Boolean hasDefaultHistory;


    private Boolean hasActiveListing;


    private String listingReason;


    private boolean recordFound;
}
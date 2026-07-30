package com.patrick.fintech.loan_backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.credit-bureau")
public class CreditBureauProperties {

    private boolean enabled = false;

    private String provider = "NONE";

    private String baseUrl;

    private String apiKey;

    private String creditReportPath = "/v1/credit-report";

    private String loanReportPath = "/v1/loan-report";

    private int connectTimeoutSeconds = 10;

    private int readTimeoutSeconds = 30;

    private int maxAttempts = 3;

    private long initialBackoffMillis = 1000;

    private int reportValidityDays = 90;

    /**
     * Never automatically replace a failed real bureau result
     * with a fake/simulated score in production.
     */
    private boolean allowSimulation = false;
}
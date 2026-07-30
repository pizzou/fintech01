
package com.patrick.fintech.loan_backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.credit-bureau")
public class CreditBureauProperties {

    /**
     * Enables/disables credit bureau integration.
     *
     * When false, the application can still start and operate
     * without making external credit bureau requests.
     */
    private boolean enabled = false;

    /**
     * Credit bureau provider name.
     *
     * Example:
     * TRANSUNION_RW
     * CRB_AFRICA
     */
    private String provider = "TRANSUNION_RW";

    /**
     * Base URL of the external credit bureau API.
     */
    private String baseUrl = "";

    /**
     * API key used when communicating with the provider.
     */
    private String apiKey = "";

    /**
     * Connection timeout in seconds.
     */
    private int connectTimeoutSeconds = 10;

    /**
     * Read timeout in seconds.
     */
    private int readTimeoutSeconds = 30;

    /**
     * Number of days for which a credit report remains valid.
     */
    private int reportValidityDays = 90;

    /**
     * API path used when requesting a credit report.
     */
    private String creditReportPath = "/credit-reports";

    /**
     * API path used when reporting a loan.
     */
    private String loanReportPath = "/loan-reports";
}


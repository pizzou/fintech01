
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
     * Maximum time to establish a connection to the credit bureau provider.
     */
    private int connectTimeoutSeconds = 10;

    /**
     * Maximum time waiting for the credit bureau provider response.
     */
    private int readTimeoutSeconds = 30;

    /**
     * Optional provider base URL.
     */
    private String baseUrl;

    /**
     * Optional API key.
     */
    private String apiKey;

    /**
     * Provider name.
     */
    private String provider = "TRANSUNION_RW";
}


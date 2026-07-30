package com.patrick.fintech.loan_backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AppConfig implements AsyncConfigurer {

    /**
     * Request ID filter.
     *
     * Adds/tracks a request ID for tracing requests across the backend.
     */
    @Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<RequestIdFilter> requestIdFilter() {

        var registration =
                new org.springframework.boot.web.servlet.FilterRegistrationBean<RequestIdFilter>(
                        new RequestIdFilter()
                );

        registration.setOrder(org.springframework.core.Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");

        return registration;
    }

    /**
     * Async executor.
     *
     * Used by @Async services such as:
     * - audit logging
     * - notifications
     * - email
     * - webhooks
     * - background processing
     */
    @Override
    @Bean(name = "taskExecutor")
    public Executor getAsyncExecutor() {

        ThreadPoolTaskExecutor executor =
                new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("loansaas-async-");

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);

        executor.initialize();

        return executor;
    }

    /**
     * Swagger / OpenAPI configuration.
     */
    @Bean
    public OpenAPI openAPI() {

        return new OpenAPI()

                .info(
                        new Info()
                                .title("LoanSaaS Pro — Enterprise Loan Management API")
                                .version("2.0.0")
                                .description(
                                        "Multi-tenant, international-grade loan management platform.\n\n"
                                                + "Features: multi-org isolation, loan management, "
                                                + "FX support, webhook events, audit logs, "
                                                + "payments, risk scoring and tenant isolation."
                                )
                                .contact(
                                        new Contact()
                                                .name("Support")
                                                .email("support@loansaas.io")
                                )
                )

                .addSecurityItem(
                        new SecurityRequirement()
                                .addList("Bearer Auth")
                )

                .components(
                        new Components()
                                .addSecuritySchemes(
                                        "Bearer Auth",
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")
                                )
                );
    }
}


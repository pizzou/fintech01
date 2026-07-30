package com.patrick.fintech.loan_backend.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;


@Configuration
public class RestTemplateConfig {


    @Bean
    public RestTemplate restTemplate(
            CreditBureauProperties properties
    ) {


        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();


        factory.setConnectTimeout(
                properties.getConnectTimeoutSeconds() * 1000
        );


        factory.setReadTimeout(
                properties.getReadTimeoutSeconds() * 1000
        );


        return new RestTemplate(factory);
    }
}

package com.quickcart.chat.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class GatewayClientConfig {

    /** Talks to the existing api-gateway only. Timeouts are the contract's 2 s connect / 5 s read. */
    @Bean
    public RestClient gatewayRestClient(RestClient.Builder builder,
                                 @Value("${quickcart.gateway.base-url}") String baseUrl,
                                 @Value("${quickcart.gateway.connect-timeout-ms:2000}") int connectTimeoutMs,
                                 @Value("${quickcart.gateway.read-timeout-ms:5000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return builder.clone()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("Accept", "application/json")
                .build();
    }
}
package com.quickcart.chat.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.quickcart.chat.client.GatewayCallException.Kind;
import com.quickcart.chat.security.RequestIdFilter;
import java.net.URI;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

/** Read-only GET calls to the existing gateway, always with the end user's own token. */
@Component
public class GatewayClient {

    private static final Logger log = LoggerFactory.getLogger(GatewayClient.class);

    private final RestClient restClient;

    public GatewayClient(@Qualifier("gatewayRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * @param token    the user's JWT (never logged)
     * @param toolName used for logging only
     * @param uri      builds the path and query relative to the gateway base URL
     */
    public JsonNode get(String token, String toolName, Function<UriBuilder, URI> uri) {
        long start = System.nanoTime();
        String outcome = "OK";
        try {
            JsonNode body = restClient.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .headers(h -> {
                        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
                        if (requestId != null) {
                            h.set(RequestIdFilter.HEADER, requestId);      // correlate logs across services
                        }
                    })
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null) {
                outcome = "EMPTY_BODY";
                throw new GatewayCallException(Kind.UNAVAILABLE);
            }
            return body;
        } catch (HttpClientErrorException e) {
            int status = e.getStatusCode().value();
            outcome = "HTTP_" + status;
            if (status == 401 || status == 403) {
                throw new GatewayCallException(Kind.UNAUTHORIZED);
            }
            throw new GatewayCallException(status == 404 ? Kind.NOT_FOUND : Kind.REJECTED);
        } catch (HttpServerErrorException | ResourceAccessException e) {   // 5xx, connect or read timeout
            outcome = "UNAVAILABLE";
            throw new GatewayCallException(Kind.UNAVAILABLE);
        } catch (RestClientException e) {
            outcome = "BAD_RESPONSE";
            throw new GatewayCallException(Kind.UNAVAILABLE);
        } finally {
            log.info("gateway call tool={} outcome={} ms={}", toolName, outcome,
                    (System.nanoTime() - start) / 1_000_000);              // no arguments, no body, no token
        }
    }
}
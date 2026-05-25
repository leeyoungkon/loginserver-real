package com.example.loginserver.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@RestController
@RequiredArgsConstructor
public class DeliveryProxyController {

    private final RestClient restClient;

    @Value("${ingress.url}")
    private String ingressUrl;

    @GetMapping("/delivery")
    public ResponseEntity<Object> proxyDelivery(@CookieValue(value = "token", required = false) String token) {
        if (token == null || token.isBlank()) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Authentication required: token cookie not found");
        }

        try {
            Object deliveryResponse = callDeliveryWithFallback(token);

            return ResponseEntity.ok(deliveryResponse);
        } catch (RestClientResponseException e) {
            return ResponseEntity
                    .status(e.getStatusCode())
                    .body("Delivery service error: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .body("Delivery service call failed: " + e.getMessage());
        }
    }

    private Object callDeliveryWithFallback(String token) {
        String[] candidates = {"/delivery/status", "/delivery"};

        RestClientResponseException lastException = null;
        for (String path : candidates) {
            try {
                return restClient.get()
                        .uri(ingressUrl + path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .retrieve()
                        .body(String.class);
            } catch (RestClientResponseException e) {
                lastException = e;
                if (e.getStatusCode().value() != HttpStatus.NOT_FOUND.value()) {
                    throw e;
                }
            }
        }

        if (lastException == null) {
            throw new IllegalStateException("Delivery service call failed with unknown error");
        }
        throw lastException;
    }
}

package com.example.loginserver.controller;

import com.example.loginserver.dto.AuthResponse;
import com.example.loginserver.dto.LoginRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class LoginController {

    private final RestClient restClient;

    @Value("${auth.server.url}")
    private String authServerUrl;

    @Value("${auth.server.register.url}")
    private String authServerRegisterUrl;

    @Value("${ingress.url}")
    private String ingressUrl;

    /**
     * 사용자 id/password 로그인 → Auth 서버에서 JWT 발급 → Ingress를 통해 Order 서비스 호출
     */
    @PostMapping("/login")
    public ResponseEntity<Object> login(@RequestBody LoginRequest loginRequest) {

        // 1. Auth 서버에 인증 요청하여 JWT 발급
        AuthResponse authResponse;
        try {
            authResponse = restClient.post()
                    .uri(authServerUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(loginRequest)
                    .retrieve()
                    .body(AuthResponse.class);
        } catch (RestClientResponseException e) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Authentication failed: " + e.getResponseBodyAsString());
        }

        if (authResponse == null || authResponse.getToken() == null) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Authentication failed: token not received");
        }

        // 2. 발급된 JWT를 Authorization 헤더에 담아 Ingress를 통해 Order 서비스 요청
        try {
            Object orderResponse = callDeliveryWithFallback(authResponse.getToken());

            return ResponseEntity.ok(orderResponse);
        } catch (RestClientResponseException e) {
            return ResponseEntity
                    .status(e.getStatusCode())
                    .body("Order service error: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .body("Order service call failed: " + e.getMessage());
        }
    }

    /**
     * 사용자 id/password 로그인 → Auth 서버에서 JWT 발급 결과를 그대로 반환
     */
    @PostMapping("/login/token")
    public ResponseEntity<Object> loginForToken(@RequestBody LoginRequest loginRequest) {
        try {
            AuthResponse authResponse = restClient.post()
                    .uri(authServerUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(loginRequest)
                    .retrieve()
                    .body(AuthResponse.class);

            if (authResponse == null || authResponse.getToken() == null) {
                return ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .body("Authentication failed: token not received");
            }

            return ResponseEntity.ok(authResponse);
        } catch (RestClientResponseException e) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Authentication failed: " + e.getResponseBodyAsString());
        }
    }

    /**
     * 사용자 id/password 회원가입 요청 → Auth 서버를 통해 DB에 사용자 등록
     */
    @PostMapping("/register")
    public ResponseEntity<Object> register(@RequestBody LoginRequest loginRequest) {
        try {
            Object registerResponse = restClient.post()
                    .uri(authServerRegisterUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(loginRequest)
                    .retrieve()
                    .body(Object.class);

            return ResponseEntity.status(HttpStatus.CREATED).body(registerResponse);
        } catch (RestClientResponseException e) {
            return ResponseEntity
                    .status(e.getStatusCode())
                    .body("Register failed: " + e.getResponseBodyAsString());
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
            throw new IllegalStateException("Order service call failed with unknown error");
        }
        throw lastException;
    }
}

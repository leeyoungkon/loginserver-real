package com.example.loginserver.dto;

import lombok.Data;

@Data
public class AuthResponse {
    private String token;
    private String tokenType;
}

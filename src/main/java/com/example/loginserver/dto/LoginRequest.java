package com.example.loginserver.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String id;
    private String password;
}

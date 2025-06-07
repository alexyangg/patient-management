package com.pm.authservice.dto;

public class LoginResponseDTO {
    private final String token; // once token has been initialized, cannot reinitialize

    // since only 1 field, initialize object and field using constructor instead of using setter
    public LoginResponseDTO(String token) {
        this.token = token;
    }

    public String getToken() {
        return token;
    }
}

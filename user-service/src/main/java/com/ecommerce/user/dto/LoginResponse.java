package com.ecommerce.user.dto;

public record LoginResponse(
        String token,
        String type,
        Long userId,
        String email,
        String fullName,
        String role
) {
    public LoginResponse(String token, Long userId, String email, String fullName, String role) {
        this(token, "Bearer", userId, email, fullName, role);
    }
}

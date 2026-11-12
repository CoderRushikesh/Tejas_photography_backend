package com.tejas.photography.dto;

import jakarta.validation.constraints.NotBlank;

/** role is "user" or "admin" (the two tabs of the login screen). */
public record LoginRequest(
        String role,

        @NotBlank(message = "Email is required")
        String email,

        @NotBlank(message = "Password is required")
        String password) {
}

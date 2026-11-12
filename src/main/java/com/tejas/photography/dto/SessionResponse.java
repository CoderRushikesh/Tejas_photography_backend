package com.tejas.photography.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tejas.photography.entity.User;

import java.util.Locale;

/** Exactly what the frontend stores as its session: { role, name, email, phone, token }. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SessionResponse(String role, String name, String email, String phone, String token) {

    public static SessionResponse of(User u, String token) {
        return new SessionResponse(u.getRole().name().toLowerCase(Locale.ROOT), u.getFullName(),
                u.getEmail(), u.getPhone(), token);
    }
}

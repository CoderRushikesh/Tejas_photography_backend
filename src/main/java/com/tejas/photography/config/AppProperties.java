package com.tejas.photography.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Typed binding for every "app.*" property in application.properties.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @DefaultValue("Asia/Kolkata") String timezone,
        @DefaultValue("91") String defaultCountryCode,
        Jwt jwt,
        Cors cors,
        Admin admin,
        BookingRules booking,
        Studio studio,
        WhatsApp whatsapp) {

    public record Jwt(String secret, @DefaultValue("86400000") long expirationMs) {
    }

    public record Cors(@DefaultValue({"http://localhost:3000", "http://localhost:5173"}) List<String> allowedOrigins) {
    }

    public record Admin(String name, String email, String phone, String password) {
    }

    public record BookingRules(@DefaultValue("1") int maxConfirmedPerDay) {
    }

    public record Studio(@DefaultValue("Tejas Photography") String name, String contactPhone) {
    }

    public record WhatsApp(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("https://graph.facebook.com/v21.0") String apiBaseUrl,
            String phoneNumberId,
            String accessToken,
            @DefaultValue("template") String mode,
            @DefaultValue("en") String languageCode,
            @DefaultValue("booking_confirmed") String confirmedTemplate) {
    }
}

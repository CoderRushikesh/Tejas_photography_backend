package com.tejas.photography.enums;

import com.tejas.photography.exception.BadRequestException;

import java.util.Locale;

/** Stored as NEW / CONFIRMED / REJECTED, exposed to the frontend in lower case (new / confirmed / rejected). */
public enum InquiryStatus {
    NEW, CONFIRMED, REJECTED;

    public String api() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static InquiryStatus parse(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Status is required");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Status must be one of: new, confirmed, rejected");
        }
    }
}

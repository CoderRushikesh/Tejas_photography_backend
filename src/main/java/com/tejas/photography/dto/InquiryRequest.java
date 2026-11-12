package com.tejas.photography.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of POST /api/inquiries. date is "yyyy-MM-dd" (what an HTML date input produces) or empty. */
public record InquiryRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must be at most 100 characters")
        String name,

        @NotBlank(message = "Phone number is required")
        String phone,

        @Email(message = "Enter a valid email address")
        @Size(max = 150, message = "Email must be at most 150 characters")
        String email,

        String date,

        @Size(max = 100, message = "Service must be at most 100 characters")
        String service,

        @Size(max = 2000, message = "Message must be at most 2000 characters")
        String message) {
}

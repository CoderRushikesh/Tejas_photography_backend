package com.tejas.photography.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tejas.photography.entity.Inquiry;

import java.time.Instant;

/** Field names match the frontend: id, name, phone, email, date, service, message, status, createdAt. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InquiryResponse(
        Long id,
        String name,
        String phone,
        String email,
        String date,
        String service,
        String message,
        String status,
        Instant createdAt,
        String whatsappStatus,
        String whatsappError,
        String whatsappLink) {

    public static InquiryResponse from(Inquiry i) {
        return from(i, null);
    }

    public static InquiryResponse from(Inquiry i, String whatsappLink) {
        return new InquiryResponse(i.getId(), i.getName(), i.getPhone(), i.getEmail(),
                i.getEventDate() == null ? null : i.getEventDate().toString(),
                i.getService(), i.getMessage(), i.getStatus().api(), i.getCreatedAt(),
                i.getWhatsappStatus().name(), i.getWhatsappError(), whatsappLink);
    }
}

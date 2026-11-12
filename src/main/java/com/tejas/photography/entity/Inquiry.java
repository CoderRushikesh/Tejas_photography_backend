package com.tejas.photography.entity;

import com.tejas.photography.enums.InquiryStatus;
import com.tejas.photography.enums.WhatsAppStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/** One customer enquiry / date request. This is what the admin dashboard lists. */
@Entity
@Table(name = "inquiries", indexes = {
        @Index(name = "idx_inquiry_date_status", columnList = "event_date,status"),
        @Index(name = "idx_inquiry_user", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Set only when the customer was logged in while sending the enquiry. */
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 100)
    private String name;

    /** International format, e.g. +919876543210 */
    @Column(nullable = false, length = 20)
    private String phone;

    @Column(length = 150)
    private String email;

    /** Requested shoot date; optional (the contact form allows leaving it empty). */
    @Column(name = "event_date")
    private LocalDate eventDate;

    @Column(length = 100)
    private String service;

    @Column(length = 2000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InquiryStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "whatsapp_status", nullable = false, length = 20)
    private WhatsAppStatus whatsappStatus;

    @Column(name = "whatsapp_message_id", length = 120)
    private String whatsappMessageId;

    @Column(name = "whatsapp_error", length = 500)
    private String whatsappError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}

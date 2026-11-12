package com.tejas.photography.dto;

import com.tejas.photography.enums.WhatsAppStatus;

/** Outcome of one WhatsApp send attempt. */
public record NotificationResult(WhatsAppStatus status, String messageId, String error) {
}

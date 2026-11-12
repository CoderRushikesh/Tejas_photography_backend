package com.tejas.photography.enums;

public enum WhatsAppStatus {
    /** No message has been sent yet for the current booking status. */
    NOT_SENT,
    /** WhatsApp Cloud API accepted the message. */
    SENT,
    /** WhatsApp Cloud API call failed (see whatsappError). */
    FAILED,
    /** Automatic sending is turned off (app.whatsapp.enabled=false) - use the wa.me link. */
    DISABLED
}

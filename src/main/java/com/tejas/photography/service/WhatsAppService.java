package com.tejas.photography.service;

import com.tejas.photography.config.AppProperties;
import com.tejas.photography.dto.NotificationResult;
import com.tejas.photography.entity.Inquiry;
import com.tejas.photography.enums.WhatsAppStatus;
import com.tejas.photography.util.PhoneUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sends the "your date is confirmed" message to the customer through the WhatsApp Business Cloud API (Meta),
 * and builds a wa.me click-to-chat link as the manual fallback (the same link the dashboard button uses).
 * <p>
 * This class never throws: every failure is returned as a {@link NotificationResult}, so a WhatsApp
 * problem can never undo the confirmation that was already saved.
 */
@Slf4j
@Service
public class WhatsAppService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private final AppProperties props;
    private final RestClient restClient;

    public WhatsAppService(AppProperties props, RestClient.Builder builder) {
        this.props = props;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        this.restClient = builder
                .baseUrl(props.whatsapp().apiBaseUrl())
                .requestFactory(factory)
                .build();
    }

    // ------------------------------------------------------------------ public API

    public NotificationResult sendConfirmation(Inquiry i) {
        AppProperties.WhatsApp cfg = props.whatsapp();
        if (!cfg.enabled()) {
            return new NotificationResult(WhatsAppStatus.DISABLED, null,
                    "Automatic WhatsApp sending is disabled. Use the WhatsApp button to message the customer.");
        }
        Map<String, Object> payload = "template".equalsIgnoreCase(cfg.mode())
                ? templatePayload(i, cfg.confirmedTemplate())
                : textPayload(i, confirmationText(i));
        return send(payload);
    }

    /** Same wording as the dashboard's WhatsApp button. */
    public String confirmationText(Inquiry i) {
        String service = StringUtils.hasText(i.getService()) ? i.getService() : "photoshoot";
        String date = i.getEventDate() == null ? "your requested date" : i.getEventDate().format(DATE_FMT);
        return "Hi " + i.getName() + ", your " + service + " on " + date + " with " + props.studio().name()
                + " is confirmed ✅. We will contact you with the next steps. Thank you!";
    }

    /** https://wa.me/<digits>?text=<url-encoded message> - opens WhatsApp with the message pre-filled. */
    public String waMeLink(Inquiry i) {
        String encoded = URLEncoder.encode(confirmationText(i), StandardCharsets.UTF_8).replace("+", "%20");
        return "https://wa.me/" + PhoneUtil.digitsOnly(i.getPhone()) + "?text=" + encoded;
    }

    // ------------------------------------------------------------------ internals

    private Map<String, Object> textPayload(Inquiry i, String body) {
        Map<String, Object> payload = envelope(i, "text");
        payload.put("text", Map.of("preview_url", false, "body", body));
        return payload;
    }

    /**
     * Template body to create in Meta Business Manager (3 variables):
     * "Hi {{1}}, your {{2}} on {{3}} with Tejas Photography is confirmed. We will contact you with the next steps."
     */
    private Map<String, Object> templatePayload(Inquiry i, String templateName) {
        List<String> values = List.of(
                i.getName(),
                StringUtils.hasText(i.getService()) ? i.getService() : "photoshoot",
                i.getEventDate() == null ? "your requested date" : i.getEventDate().format(DATE_FMT));
        List<Map<String, String>> parameters = values.stream()
                .map(v -> Map.of("type", "text", "text", clean(v)))
                .toList();
        Map<String, Object> template = Map.of(
                "name", templateName,
                "language", Map.of("code", props.whatsapp().languageCode()),
                "components", List.of(Map.of("type", "body", "parameters", parameters)));
        Map<String, Object> payload = envelope(i, "template");
        payload.put("template", template);
        return payload;
    }

    private Map<String, Object> envelope(Inquiry i, String type) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("recipient_type", "individual");
        payload.put("to", PhoneUtil.digitsOnly(i.getPhone()));
        payload.put("type", type);
        return payload;
    }

    private NotificationResult send(Map<String, Object> payload) {
        AppProperties.WhatsApp cfg = props.whatsapp();
        if (!StringUtils.hasText(cfg.phoneNumberId()) || !StringUtils.hasText(cfg.accessToken())) {
            return new NotificationResult(WhatsAppStatus.FAILED, null,
                    "WhatsApp is enabled but WHATSAPP_PHONE_NUMBER_ID / WHATSAPP_ACCESS_TOKEN are not set");
        }
        try {
            Map<?, ?> response = restClient.post()
                    .uri("/{phoneNumberId}/messages", cfg.phoneNumberId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + cfg.accessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(Map.class);
            String messageId = extractMessageId(response);
            log.info("WhatsApp message accepted for {} (id={})", payload.get("to"), messageId);
            return new NotificationResult(WhatsAppStatus.SENT, messageId, null);
        } catch (RestClientResponseException ex) {
            String error = "WhatsApp API " + ex.getStatusCode().value() + ": " + abbreviate(ex.getResponseBodyAsString());
            log.warn("WhatsApp send failed: {}", error);
            return new NotificationResult(WhatsAppStatus.FAILED, null, error);
        } catch (Exception ex) {
            log.warn("WhatsApp send failed", ex);
            return new NotificationResult(WhatsAppStatus.FAILED, null, "Could not reach WhatsApp API: " + ex.getMessage());
        }
    }

    private static String extractMessageId(Map<?, ?> response) {
        if (response != null && response.get("messages") instanceof List<?> list && !list.isEmpty()
                && list.get(0) instanceof Map<?, ?> first && first.get("id") != null) {
            return String.valueOf(first.get("id"));
        }
        return null;
    }

    /** Template parameters may not contain newlines/tabs or long runs of spaces. */
    private static String clean(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String cleaned = value.replaceAll("[\\r\\n\\t]+", " ").replaceAll(" {2,}", " ").trim();
        return cleaned.length() > 500 ? cleaned.substring(0, 500) : cleaned;
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 350 ? s.substring(0, 350) + "..." : s;
    }
}

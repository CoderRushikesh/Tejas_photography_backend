package com.tejas.photography.service;

import com.tejas.photography.config.AppProperties;
import com.tejas.photography.dto.DateCheckResponse;
import com.tejas.photography.dto.InquiryRequest;
import com.tejas.photography.dto.InquiryResponse;
import com.tejas.photography.dto.NotificationResult;
import com.tejas.photography.entity.Inquiry;
import com.tejas.photography.enums.InquiryStatus;
import com.tejas.photography.enums.WhatsAppStatus;
import com.tejas.photography.exception.BadRequestException;
import com.tejas.photography.exception.ConflictException;
import com.tejas.photography.exception.ResourceNotFoundException;
import com.tejas.photography.repository.InquiryRepository;
import com.tejas.photography.util.PhoneUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * All enquiry / date-availability logic.
 * <p>
 * Deliberately NOT annotated with @Transactional: the database change (status + double-booking check) runs in its
 * own short transaction, and the slow WhatsApp HTTP call happens afterwards, outside any transaction. A WhatsApp
 * failure therefore never rolls back a confirmation and never holds a database lock.
 */
@Service
public class InquiryService {

    private final InquiryRepository repo;
    private final WhatsAppService whatsApp;
    private final AppProperties props;
    private final Clock clock;
    private final TransactionTemplate tx;

    public InquiryService(InquiryRepository repo, WhatsAppService whatsApp, AppProperties props, Clock clock,
                          PlatformTransactionManager txManager) {
        this.repo = repo;
        this.whatsApp = whatsApp;
        this.props = props;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
    }

    // ---------------------------------------------------------------- public (website visitors)

    /** GET /api/dates/check?date=yyyy-MM-dd */
    public DateCheckResponse checkDate(String rawDate) {
        LocalDate date = parseDate(rawDate);
        if (date == null) {
            throw new BadRequestException("date is required (yyyy-MM-dd)");
        }
        long live = repo.countByEventDateAndStatusNot(date, InquiryStatus.REJECTED);
        long confirmed = repo.countByEventDateAndStatus(date, InquiryStatus.CONFIRMED);
        return new DateCheckResponse(date.toString(), live, confirmed >= props.booking().maxConfirmedPerDay());
    }

    /** POST /api/inquiries - anyone can send one; if the caller is logged in it is linked to their account. */
    public InquiryResponse create(InquiryRequest req, Long userId) {
        LocalDate date = parseDate(req.date());
        if (date != null && date.isBefore(LocalDate.now(clock))) {
            throw new BadRequestException("The selected date is in the past");
        }
        Inquiry inquiry = Inquiry.builder()
                .userId(userId)
                .name(req.name().trim())
                .phone(PhoneUtil.normalize(req.phone(), props.defaultCountryCode()))
                .email(StringUtils.hasText(req.email()) ? req.email().trim().toLowerCase() : null)
                .eventDate(date)
                .service(blankToNull(req.service()))
                .message(blankToNull(req.message()))
                .status(InquiryStatus.NEW)
                .whatsappStatus(WhatsAppStatus.NOT_SENT)
                .build();
        return InquiryResponse.from(repo.save(inquiry));
    }

    public List<InquiryResponse> mine(Long userId) {
        return repo.findByUserIdOrderByCreatedAtDesc(userId).stream().map(InquiryResponse::from).toList();
    }

    // ---------------------------------------------------------------- admin

    /** GET /api/inquiries (ADMIN) - newest first. */
    public List<InquiryResponse> listAll() {
        return repo.findAllByOrderByCreatedAtDesc().stream().map(InquiryResponse::from).toList();
    }

    /** PATCH /api/inquiries/{id}/status (ADMIN) - confirm, reject, or put back to new. */
    public InquiryResponse setStatus(Long id, String rawStatus) {
        InquiryStatus target = InquiryStatus.parse(rawStatus);

        // 1) short DB transaction: lock the day, enforce the capacity rule, save the new status
        StatusChange change = tx.execute(txStatus -> {
            Inquiry inquiry = repo.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Enquiry " + id + " not found"));
            InquiryStatus previous = inquiry.getStatus();
            if (previous == target) {
                return new StatusChange(inquiry, false);
            }
            if (target == InquiryStatus.CONFIRMED && inquiry.getEventDate() != null) {
                List<Inquiry> sameDay = repo.lockAllByEventDate(inquiry.getEventDate());
                long alreadyConfirmed = sameDay.stream()
                        .filter(x -> x.getStatus() == InquiryStatus.CONFIRMED && !x.getId().equals(id))
                        .count();
                if (alreadyConfirmed >= props.booking().maxConfirmedPerDay()) {
                    throw new ConflictException("This date is already booked. Reject this enquiry or pick another date.");
                }
            }
            inquiry.setStatus(target);
            inquiry.setConfirmedAt(target == InquiryStatus.CONFIRMED ? Instant.now() : null);
            if (target != InquiryStatus.CONFIRMED) {
                inquiry.setWhatsappStatus(WhatsAppStatus.NOT_SENT);
                inquiry.setWhatsappMessageId(null);
                inquiry.setWhatsappError(null);
            }
            return new StatusChange(repo.save(inquiry), target == InquiryStatus.CONFIRMED);
        });

        Inquiry saved = change.inquiry();
        if (!change.justConfirmed()) {
            return InquiryResponse.from(saved);
        }

        // 2) outside any transaction: message the customer, then record what happened
        NotificationResult result = whatsApp.sendConfirmation(saved);
        Inquiry updated = tx.execute(txStatus -> {
            Inquiry fresh = repo.findById(id).orElse(saved);
            fresh.setWhatsappStatus(result.status());
            fresh.setWhatsappMessageId(result.messageId());
            fresh.setWhatsappError(result.error());
            return repo.save(fresh);
        });
        return InquiryResponse.from(updated, whatsApp.waMeLink(updated));
    }

    // ---------------------------------------------------------------- helpers

    private record StatusChange(Inquiry inquiry, boolean justConfirmed) {
    }

    private static LocalDate parseDate(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException ex) {
            throw new BadRequestException("Date must look like 2026-12-31");
        }
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}

package com.tejas.photography.controller;

import com.tejas.photography.dto.DateCheckResponse;
import com.tejas.photography.dto.InquiryRequest;
import com.tejas.photography.dto.InquiryResponse;
import com.tejas.photography.dto.StatusRequest;
import com.tejas.photography.security.UserPrincipal;
import com.tejas.photography.service.InquiryService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Every endpoint the React app calls besides login/register. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class InquiryController {

    private final InquiryService service;

    /** Public: is this date free? */
    @GetMapping("/dates/check")
    public DateCheckResponse checkDate(@RequestParam("date") String date) {
        return service.checkDate(date);
    }

    /** Public: send an enquiry (principal is null for anonymous visitors). */
    @PostMapping("/inquiries")
    public ResponseEntity<InquiryResponse> create(@Valid @RequestBody InquiryRequest request,
                                                  @AuthenticationPrincipal UserPrincipal principal) {
        Long userId = principal == null ? null : principal.getId();
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request, userId));
    }

    /** Logged-in customer: their own enquiries. */
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/inquiries/mine")
    public List<InquiryResponse> mine(@AuthenticationPrincipal UserPrincipal principal) {
        return service.mine(principal.getId());
    }

    /** Admin: every enquiry, newest first. */
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/inquiries")
    public List<InquiryResponse> listAll() {
        return service.listAll();
    }

    /** Admin: confirm / reject. Confirming triggers the WhatsApp message to the customer. */
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/inquiries/{id}/status")
    public InquiryResponse setStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return service.setStatus(id, request.status());
    }
}

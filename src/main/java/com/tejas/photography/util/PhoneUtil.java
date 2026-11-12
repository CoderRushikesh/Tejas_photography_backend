package com.tejas.photography.util;

import com.tejas.photography.exception.BadRequestException;

public final class PhoneUtil {

    private PhoneUtil() {
    }

    /**
     * Converts whatever the customer typed into "+<countrycode><number>".
     * <ul>
     *   <li>"98765 43210"        -> +919876543210 (default country code added)</li>
     *   <li>"09876543210"        -> +919876543210</li>
     *   <li>"+1 415 555 2671"    -> +14155552671</li>
     *   <li>"919876543210"       -> +919876543210</li>
     * </ul>
     */
    public static String normalize(String raw, String defaultCountryCode) {
        if (raw == null || raw.isBlank()) {
            throw new BadRequestException("Phone number is required");
        }
        boolean hasPlus = raw.trim().startsWith("+");
        String digits = raw.replaceAll("[^0-9]", "");
        if (!hasPlus) {
            if (digits.startsWith("00")) {
                digits = digits.substring(2);
            } else if (digits.length() == 10) {
                digits = defaultCountryCode + digits;
            } else if (digits.length() == 11 && digits.startsWith("0")) {
                digits = defaultCountryCode + digits.substring(1);
            }
        }
        if (digits.length() < 8 || digits.length() > 15) {
            throw new BadRequestException("Please enter a valid phone number with country code");
        }
        return "+" + digits;
    }

    /** WhatsApp APIs and wa.me links want digits only (no plus sign). */
    public static String digitsOnly(String phone) {
        return phone == null ? "" : phone.replaceAll("[^0-9]", "");
    }
}

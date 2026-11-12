package com.tejas.photography.dto;

/** count = live (not rejected) enquiries for the day, booked = the day is already full. */
public record DateCheckResponse(String date, long count, boolean booked) {
}

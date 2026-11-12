package com.tejas.photography.repository;

import com.tejas.photography.entity.Inquiry;
import com.tejas.photography.enums.InquiryStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

    List<Inquiry> findAllByOrderByCreatedAtDesc();

    List<Inquiry> findByUserIdOrderByCreatedAtDesc(Long userId);

    long countByEventDateAndStatusNot(LocalDate eventDate, InquiryStatus status);

    long countByEventDateAndStatus(LocalDate eventDate, InquiryStatus status);

    /**
     * Row-locks every enquiry of a day (SELECT ... FOR UPDATE). Two admins confirming the same date at the
     * same moment are therefore serialised, so the "max confirmed per day" rule can never be broken.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inquiry i where i.eventDate = :date")
    List<Inquiry> lockAllByEventDate(@Param("date") LocalDate date);
}

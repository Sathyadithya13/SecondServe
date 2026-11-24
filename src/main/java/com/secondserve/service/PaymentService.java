package com.secondserve.service;

import com.secondserve.entity.Payment;
import com.secondserve.entity.Reservation;
import com.secondserve.entity.ReservationStatus;
import com.secondserve.entity.Role;
import com.secondserve.entity.User;

import com.secondserve.repository.PaymentRepository;
import com.secondserve.repository.ReservationRepository;
import com.secondserve.repository.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;

    public PaymentService(
            PaymentRepository paymentRepository,
            ReservationRepository reservationRepository,
            UserRepository userRepository) {

        this.paymentRepository = paymentRepository;
        this.reservationRepository = reservationRepository;
        this.userRepository = userRepository;
    }

    // Confirm a free checkout for a reservation.
    @Transactional
    public Payment confirmFreePayment(String email, Long reservationId) {

        User recipient = userRepository.findByEmail(
                email.trim().toLowerCase(Locale.ROOT)
        ).orElseThrow(() ->
                new IllegalArgumentException("User account not found.")
        );

        if (recipient.getRole() != Role.RECIPIENT) {
            throw new IllegalArgumentException(
                    "Only recipients can confirm free checkout."
            );
        }

        if (reservationId == null) {
            throw new IllegalArgumentException(
                    "A valid reservation ID is required."
            );
        }

        // Lock the reservation to prevent simultaneous duplicate records.
        Reservation reservation = reservationRepository
                .findByIdForUpdate(reservationId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Reservation not found.")
                );

        if (!reservation.getRecipient().getId().equals(recipient.getId())) {
            throw new IllegalArgumentException(
                    "You cannot confirm checkout for another recipient's reservation."
            );
        }

        // Return the existing record if checkout was already confirmed.
        Payment existingPayment = paymentRepository
                .findByReservation_Id(reservationId)
                .orElse(null);

        if (existingPayment != null) {
            return existingPayment;
        }

        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new IllegalStateException(
                    "Free checkout is available only for confirmed reservations."
            );
        }

        Payment payment = new Payment();
        payment.setReservation(reservation);

        // The amount is always zero. It is never accepted from the browser.
        // Payment entity defaults to ₹0.00 and FREE_COMPLETED.

        return paymentRepository.save(payment);
    }

    // View the authenticated recipient's transaction history.
    @Transactional(readOnly = true)
    public List<Payment> getMyPayments(String email) {

        User recipient = userRepository.findByEmail(
                email.trim().toLowerCase(Locale.ROOT)
        ).orElseThrow(() ->
                new IllegalArgumentException("User account not found.")
        );

        if (recipient.getRole() != Role.RECIPIENT) {
            throw new IllegalArgumentException(
                    "Only recipients can view these transactions."
            );
        }

        return paymentRepository
                .findByReservation_Recipient_EmailOrderByCreatedAtDesc(
                        recipient.getEmail()
                );
    }
}
package com.secondserve.controller;

import com.secondserve.entity.Payment;
import com.secondserve.entity.FoodDonation;
import com.secondserve.entity.Reservation;
import com.secondserve.service.PaymentService;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@Transactional
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    // Confirm the free checkout.
    @PostMapping("/reservations/{id}/free-confirmation")
    public ResponseEntity<Map<String, Object>> confirmFreePayment(
            @PathVariable Long id,
            Authentication authentication) {

        Payment payment = paymentService.confirmFreePayment(
                authentication.getName(),
                id
        );

        return ResponseEntity.ok(toResponse(payment));
    }

    // Get the recipient's transaction history.
    @GetMapping("/mine")
    public ResponseEntity<List<Map<String, Object>>> getMyPayments(
            Authentication authentication) {

        List<Map<String, Object>> payments = paymentService
                .getMyPayments(authentication.getName())
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(payments);
    }

    private Map<String, Object> toResponse(Payment payment) {

        Map<String, Object> response = new LinkedHashMap<>();

        response.put("paymentId", payment.getId());
        response.put("amount", payment.getAmount());
        response.put("currency", payment.getCurrency());
        response.put("status", payment.getStatus());
        response.put("paymentMethod", payment.getPaymentMethod());
        response.put("createdAt", payment.getCreatedAt());

        Reservation reservation = payment.getReservation();
        FoodDonation donation = reservation.getDonation();

        response.put("reservationId", reservation.getId());
        response.put("reservationStatus", reservation.getStatus());
        response.put("foodName", donation.getFoodName());
        response.put("quantity", donation.getQuantity());
        response.put("unit", donation.getUnit());

        response.put("recipientName",
                reservation.getRecipient().getFullName());

        return response;
    }
}

package com.secondserve.controller;

import com.secondserve.entity.FulfillmentMethod;
import com.secondserve.entity.FoodDonation;
import com.secondserve.entity.Reservation;
import com.secondserve.service.ReservationService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Transactional
@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    // Create a food reservation.
    @PostMapping
    public ResponseEntity<Map<String, Object>> createReservation(
            @Valid @RequestBody CreateReservationRequest request,
            Authentication authentication) {

        FulfillmentMethod method = request.fulfillmentMethod() == null
                ? FulfillmentMethod.PICKUP
                : request.fulfillmentMethod();

        Reservation reservation = reservationService.createReservation(
                authentication.getName(),
                request.donationId(),
                method,
                request.destinationAddress(),
                request.destinationLatitude(),
                request.destinationLongitude()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(toResponse(reservation));
    }

    // View the authenticated recipient's reservation history.
    @GetMapping("/mine")
    public ResponseEntity<List<Map<String, Object>>> getMyReservations(
            Authentication authentication) {

        List<Map<String, Object>> reservations =
                reservationService.getMyReservations(
                        authentication.getName()
                )
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(reservations);
    }

    // View reservations for the authenticated donor.
    @GetMapping("/donor")
    public ResponseEntity<List<Map<String, Object>>> getDonorReservations(
            Authentication authentication) {

        List<Map<String, Object>> reservations =
                reservationService.getDonorReservations(
                        authentication.getName()
                )
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(reservations);
    }

    // Cancel a reservation.
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancelReservation(
            @PathVariable Long id,
            Authentication authentication) {

        Reservation reservation = reservationService.cancelReservation(
                authentication.getName(),
                id
        );

        return ResponseEntity.ok(toResponse(reservation));
    }

    // Confirm pickup by donor or recipient.
    @PatchMapping("/{id}/confirm-pickup")
    public ResponseEntity<Map<String, Object>> confirmPickup(
            @PathVariable Long id,
            Authentication authentication) {

        Reservation reservation = reservationService.confirmPickup(
                authentication.getName(),
                id
        );

        return ResponseEntity.ok(toResponse(reservation));
    }

    // Donor starts delivery.
    @PostMapping("/{id}/start-delivery")
    public ResponseEntity<Map<String, Object>> startDelivery(
            @PathVariable Long id,
            Authentication authentication) {

        Reservation reservation = reservationService.startDelivery(
                authentication.getName(),
                id
        );

        return ResponseEntity.ok(toResponse(reservation));
    }

    // Donor updates current GPS location.
    @PatchMapping("/{id}/delivery-location")
    public ResponseEntity<Map<String, Object>> updateDeliveryLocation(
            @PathVariable Long id,
            @Valid @RequestBody DeliveryLocationRequest request,
            Authentication authentication) {

        Reservation reservation =
                reservationService.updateDeliveryLocation(
                        authentication.getName(),
                        id,
                        request.latitude(),
                        request.longitude()
                );

        return ResponseEntity.ok(toResponse(reservation));
    }


    // Recipient retrieves the donor's latest GPS location.
    @GetMapping("/{id}/delivery-location")
    public ResponseEntity<Map<String, Object>> getDeliveryLocation(
            @PathVariable Long id,
            Authentication authentication) {

        Reservation reservation =
                reservationService.getDeliveryLocation(
                        authentication.getName(),
                        id
                );

        Map<String, Object> location = new LinkedHashMap<>();

        location.put("reservationId", reservation.getId());
        location.put("status", reservation.getStatus());
        location.put("deliveryLatitude",
                reservation.getDeliveryLatitude());
        location.put("deliveryLongitude",
                reservation.getDeliveryLongitude());
        location.put("destinationLatitude",
                reservation.getDestinationLatitude());
        location.put("destinationLongitude",
                reservation.getDestinationLongitude());
        location.put("locationUpdatedAt",
                reservation.getLocationUpdatedAt());

        return ResponseEntity.ok(location);
    }


    // Donor or recipient confirms delivery handover.
    @PatchMapping("/{id}/confirm-delivery")
    public ResponseEntity<Map<String, Object>> confirmDelivery(
            @PathVariable Long id,
            Authentication authentication) {

        Reservation reservation = reservationService.confirmDelivery(
                authentication.getName(),
                id
        );

        return ResponseEntity.ok(toResponse(reservation));
    }

    // Convert reservation details into a response.
    private Map<String, Object> toResponse(Reservation reservation) {

        Map<String, Object> response = new LinkedHashMap<>();

        response.put("reservationId", reservation.getId());
        response.put("status", reservation.getStatus());
        response.put("reservedAt", reservation.getReservedAt());
        response.put("updatedAt", reservation.getUpdatedAt());
        response.put("cancelledAt", reservation.getCancelledAt());
        response.put("completedAt", reservation.getCompletedAt());

        // Pickup confirmation details.
        response.put("recipientConfirmedPickup",
                reservation.isRecipientConfirmedPickup());

        response.put("donorConfirmedPickup",
                reservation.isDonorConfirmedPickup());

        // Fulfillment details.
        response.put("fulfillmentMethod",
                reservation.getFulfillmentMethod());

        response.put("destinationAddress",
                reservation.getDestinationAddress());

        response.put("destinationLatitude",
                reservation.getDestinationLatitude());

        response.put("destinationLongitude",
                reservation.getDestinationLongitude());

        // Live delivery GPS details.
        response.put("deliveryLatitude",
                reservation.getDeliveryLatitude());

        response.put("deliveryLongitude",
                reservation.getDeliveryLongitude());

        response.put("deliveryStartedAt",
                reservation.getDeliveryStartedAt());

        response.put("locationUpdatedAt",
                reservation.getLocationUpdatedAt());

        // Delivery confirmation details.
        response.put("recipientConfirmedDelivery",
                reservation.isRecipientConfirmedDelivery());

        response.put("donorConfirmedDelivery",
                reservation.isDonorConfirmedDelivery());

        // Food donation details.
        FoodDonation donation = reservation.getDonation();

        Map<String, Object> foodDetails = new LinkedHashMap<>();

        foodDetails.put("donationId", donation.getId());
        foodDetails.put("foodName", donation.getFoodName());
        foodDetails.put("foodCategory", donation.getFoodCategory());
        foodDetails.put("quantity", donation.getQuantity());
        foodDetails.put("unit", donation.getUnit());
        foodDetails.put("expiryTime", donation.getExpiryTime());
        foodDetails.put("pickupStart", donation.getPickupStart());
        foodDetails.put("pickupEnd", donation.getPickupEnd());
        foodDetails.put("pickupAddress", donation.getPickupAddress());
        foodDetails.put("foodDescription", donation.getFoodDescription());

        response.put("donation", foodDetails);

        // Recipient details.
        Map<String, Object> recipientDetails = new LinkedHashMap<>();

        recipientDetails.put("recipientId",
                reservation.getRecipient().getId());

        recipientDetails.put("recipientName",
                reservation.getRecipient().getFullName());

        recipientDetails.put("recipientEmail",
                reservation.getRecipient().getEmail());

        response.put("recipient", recipientDetails);

        return response;
    }

    // Request body for creating a reservation.
    public record CreateReservationRequest(

            @NotNull(message = "Donation ID is required.")
            Long donationId,

            FulfillmentMethod fulfillmentMethod,

            String destinationAddress,

            Double destinationLatitude,

            Double destinationLongitude
    ) {
    }

    // Request body for updating donor GPS location.
    public record DeliveryLocationRequest(

            @NotNull(message = "Latitude is required.")
            Double latitude,

            @NotNull(message = "Longitude is required.")
            Double longitude
    ) {
    }
}

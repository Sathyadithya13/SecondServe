
package com.secondserve.controller;

import com.secondserve.entity.FoodDonation;
import com.secondserve.service.FoodDonationService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/donations")
public class FoodDonationController {

    private final FoodDonationService donationService;

    public FoodDonationController(FoodDonationService donationService) {
        this.donationService = donationService;
    }

    // Create a donation. The service verifies that the account is a donor.
    @PostMapping
    public ResponseEntity<Map<String, Object>> createDonation(
            @Valid @RequestBody CreateDonationRequest request,
            Authentication authentication) {

        FoodDonation donation = donationService.createDonation(
                authentication.getName(),
                request.foodName(),
                request.foodCategory(),
                request.quantity(),
                request.unit(),
                request.expiryTime(),
                request.pickupStart(),
                request.pickupEnd(),
                request.pickupAddress(),
                request.foodDescription()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(
                Map.of(
                        "message", "Food donation created successfully.",
                        "donation", toResponse(donation)
                )
        );
    }

    // Get only the authenticated donor's donation history.
    @GetMapping("/mine")
    public ResponseEntity<List<Map<String, Object>>> getMyDonations(
            Authentication authentication) {

        List<Map<String, Object>> donations =
                donationService.getMyDonations(authentication.getName())
                        .stream()
                        .map(FoodDonationController::toResponse)
                        .toList();

        return ResponseEntity.ok(donations);
    }

    // Get donations that are available and within their time windows.
    @GetMapping("/available")
    public ResponseEntity<List<Map<String, Object>>> getAvailableDonations(
            Authentication authentication) {

        requireRecipient(authentication);

        List<Map<String, Object>> donations =
                donationService.getAvailableDonations()
                        .stream()
                        .map(FoodDonationController::toResponse)
                        .toList();

        return ResponseEntity.ok(donations);
    }

    // Get donation counts for the authenticated donor.
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getMyStatistics(
            Authentication authentication) {

        String email = authentication.getName();

        long total = donationService.countMyDonations(email);
        long available = donationService.countMyAvailableDonations(email);

        return ResponseEntity.ok(
                Map.of(
                        "totalDonations", total,
                        "availableDonations", available
                )
        );
    }

    private static void requireRecipient(Authentication authentication) {
        boolean isRecipient = authentication.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals("ROLE_RECIPIENT"));

        if (!isRecipient) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Only recipient accounts can browse available donations."
            );
        }
    }

    // Return a safe response without exposing the User entity or password hash.
    private static Map<String, Object> toResponse(FoodDonation donation) {
        return Map.ofEntries(
                Map.entry("id", donation.getId()),
                Map.entry("foodName", donation.getFoodName()),
                Map.entry("foodCategory", donation.getFoodCategory()),
                Map.entry("quantity", donation.getQuantity()),
                Map.entry("unit", donation.getUnit()),
                Map.entry("expiryTime", donation.getExpiryTime().toString()),
                Map.entry("pickupStart", donation.getPickupStart().toString()),
                Map.entry("pickupEnd", donation.getPickupEnd().toString()),
                Map.entry("pickupAddress", donation.getPickupAddress()),
                Map.entry("foodDescription",
                        donation.getFoodDescription() == null
                                ? ""
                                : donation.getFoodDescription()),
                Map.entry("status", donation.getStatus().name()),
                Map.entry("createdAt", donation.getCreatedAt().toString())
        );
    }

    public record CreateDonationRequest(
            @NotBlank
            @Size(max = 120)
            String foodName,

            @NotBlank
            String foodCategory,

            @NotNull
            @Min(1)
            @Max(10000)
            Integer quantity,

            @NotBlank
            String unit,

            @NotNull
            @Future
            LocalDateTime expiryTime,

            @NotNull
            @Future
            LocalDateTime pickupStart,

            @NotNull
            @Future
            LocalDateTime pickupEnd,

            @NotBlank
            @Size(max = 500)
            String pickupAddress,

            @Size(max = 1500)
            String foodDescription
    ) {
    }
}

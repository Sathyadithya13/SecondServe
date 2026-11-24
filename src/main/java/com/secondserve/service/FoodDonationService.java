
package com.secondserve.service;

import com.secondserve.entity.DonationStatus;
import com.secondserve.entity.FoodDonation;
import com.secondserve.entity.Role;
import com.secondserve.entity.User;
import com.secondserve.repository.FoodDonationRepository;
import com.secondserve.repository.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class FoodDonationService {

    private final FoodDonationRepository donationRepository;
    private final UserRepository userRepository;

    public FoodDonationService(
            FoodDonationRepository donationRepository,
            UserRepository userRepository) {
        this.donationRepository = donationRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public FoodDonation createDonation(
            String donorEmail,
            String foodName,
            String foodCategory,
            Integer quantity,
            String unit,
            LocalDateTime expiryTime,
            LocalDateTime pickupStart,
            LocalDateTime pickupEnd,
            String pickupAddress,
            String foodDescription) {

        // Identify the donor using the authenticated account email.
        User donor = userRepository.findByEmail(
                donorEmail.trim().toLowerCase(Locale.ROOT)
        ).orElseThrow(() ->
                new IllegalArgumentException("Donor account not found.")
        );

        if (donor.getRole() != Role.DONOR) {
            throw new IllegalArgumentException(
                    "Only donor accounts can create food donations."
            );
        }

        LocalDateTime now = LocalDateTime.now();

        // Validate the food name.
        if (foodName == null || foodName.isBlank()
                || foodName.trim().length() > 120) {
            throw new IllegalArgumentException(
                    "Food name is required and must be at most 120 characters."
            );
        }

        // Validate the category.
        List<String> allowedCategories = List.of(
                "COOKED_MEALS",
                "FRUITS_VEGETABLES",
                "BAKERY",
                "PACKAGED_FOOD",
                "OTHER"
        );

        if (foodCategory == null
                || !allowedCategories.contains(foodCategory)) {
            throw new IllegalArgumentException(
                    "Please select a valid food category."
            );
        }

        // Validate quantity and unit.
        if (quantity == null || quantity < 1 || quantity > 10000) {
            throw new IllegalArgumentException(
                    "Quantity must be between 1 and 10000."
            );
        }

        List<String> allowedUnits = List.of(
                "PORTIONS",
                "PACKETS",
                "KG",
                "LITRES",
                "ITEMS"
        );

        if (unit == null || !allowedUnits.contains(unit)) {
            throw new IllegalArgumentException(
                    "Please select a valid quantity unit."
            );
        }

        // Validate expiry and pickup times.
        if (expiryTime == null || !expiryTime.isAfter(now)) {
            throw new IllegalArgumentException(
                    "Expiry time must be in the future."
            );
        }

        if (pickupStart == null || !pickupStart.isAfter(now)) {
            throw new IllegalArgumentException(
                    "Pickup start time must be in the future."
            );
        }

        if (pickupEnd == null || !pickupEnd.isAfter(pickupStart)) {
            throw new IllegalArgumentException(
                    "Pickup end time must be after pickup start time."
            );
        }

        if (pickupEnd.isAfter(expiryTime)) {
            throw new IllegalArgumentException(
                    "Pickup must end no later than the expiry time."
            );
        }

        // Validate pickup address.
        if (pickupAddress == null || pickupAddress.isBlank()
                || pickupAddress.trim().length() > 500) {
            throw new IllegalArgumentException(
                    "A valid pickup address is required."
            );
        }

        // Validate optional description.
        if (foodDescription != null
                && foodDescription.length() > 1500) {
            throw new IllegalArgumentException(
                    "Food description must be at most 1500 characters."
            );
        }

        FoodDonation donation = new FoodDonation();

        donation.setDonor(donor);
        donation.setFoodName(foodName.trim());
        donation.setFoodCategory(foodCategory);
        donation.setQuantity(quantity);
        donation.setUnit(unit);
        donation.setExpiryTime(expiryTime);
        donation.setPickupStart(pickupStart);
        donation.setPickupEnd(pickupEnd);
        donation.setPickupAddress(pickupAddress.trim());

        if (foodDescription != null
                && !foodDescription.isBlank()) {
            donation.setFoodDescription(foodDescription.trim());
        }

        donation.setStatus(DonationStatus.AVAILABLE);

        return donationRepository.save(donation);
    }

    @Transactional(readOnly = true)
    public List<FoodDonation> getMyDonations(String donorEmail) {

        User donor = findUserByEmail(donorEmail);

        if (donor.getRole() != Role.DONOR) {
            throw new IllegalArgumentException(
                    "Only donors can view their donation history."
            );
        }

        return donationRepository.findByDonorOrderByCreatedAtDesc(donor);
    }

    @Transactional(readOnly = true)
    public List<FoodDonation> getAvailableDonations() {

        return donationRepository.findAvailableDonations(
                DonationStatus.AVAILABLE,
                LocalDateTime.now()
        );
    }

    @Transactional(readOnly = true)
    public long countMyDonations(String donorEmail) {

        User donor = findUserByEmail(donorEmail);

        if (donor.getRole() != Role.DONOR) {
            throw new IllegalArgumentException(
                    "Only donors can view donation statistics."
            );
        }

        return donationRepository.countByDonor(donor);
    }

    @Transactional(readOnly = true)
    public long countMyAvailableDonations(String donorEmail) {

        User donor = findUserByEmail(donorEmail);

        if (donor.getRole() != Role.DONOR) {
            throw new IllegalArgumentException(
                    "Only donors can view donation statistics."
            );
        }

        return donationRepository.countByDonorAndStatus(
                donor,
                DonationStatus.AVAILABLE
        );
    }

    private User findUserByEmail(String email) {

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException(
                    "A valid authenticated account is required."
            );
        }

        return userRepository.findByEmail(
                email.trim().toLowerCase(Locale.ROOT)
        ).orElseThrow(() ->
                new IllegalArgumentException("User account not found.")
        );
    }
}

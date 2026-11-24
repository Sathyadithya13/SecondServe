
package com.secondserve.service;

import com.secondserve.entity.DonationStatus;
import com.secondserve.entity.FoodDonation;
import com.secondserve.entity.FulfillmentMethod;
import com.secondserve.entity.Reservation;
import com.secondserve.entity.ReservationStatus;
import com.secondserve.entity.Role;
import com.secondserve.entity.User;

import com.secondserve.repository.FoodDonationRepository;
import com.secondserve.repository.ReservationRepository;
import com.secondserve.repository.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final FoodDonationRepository donationRepository;
    private final UserRepository userRepository;

    public ReservationService(
            ReservationRepository reservationRepository,
            FoodDonationRepository donationRepository,
            UserRepository userRepository) {

        this.reservationRepository = reservationRepository;
        this.donationRepository = donationRepository;
        this.userRepository = userRepository;
    }

    // Create a reservation safely.
    
// Existing method retained for backward compatibility.
@Transactional
public Reservation createReservation(
        String recipientEmail,
        Long donationId) {

    return createReservation(
            recipientEmail,
            donationId,
            FulfillmentMethod.PICKUP,
            null,
            null,
            null
    );
}

// Create a reservation with the selected fulfillment method.
@Transactional
public Reservation createReservation(
        String recipientEmail,
        Long donationId,
        FulfillmentMethod fulfillmentMethod,
        String destinationAddress,
        Double destinationLatitude,
        Double destinationLongitude) {

    User recipient = findUserByEmail(recipientEmail);

    if (recipient.getRole() != Role.RECIPIENT) {
        throw new IllegalArgumentException(
                "Only recipient accounts can reserve food."
        );
    }

    if (donationId == null) {
        throw new IllegalArgumentException(
                "A valid donation ID is required."
        );
    }

    if (fulfillmentMethod == null) {
        throw new IllegalArgumentException(
                "Please select a fulfillment method."
        );
    }

    // Validate delivery destination.
    if (fulfillmentMethod == FulfillmentMethod.DONOR_DELIVERY) {

        if (destinationAddress == null || destinationAddress.isBlank()) {
            throw new IllegalArgumentException(
                    "Delivery address is required."
            );
        }

        if (destinationLatitude == null || destinationLongitude == null
                || !Double.isFinite(destinationLatitude)
                || !Double.isFinite(destinationLongitude)
                || destinationLatitude < -90
                || destinationLatitude > 90
                || destinationLongitude < -180
                || destinationLongitude > 180) {

            throw new IllegalArgumentException(
                    "Valid delivery coordinates are required."
            );
        }
    }

    FoodDonation donation = donationRepository
            .findByIdForUpdate(donationId)
            .orElseThrow(() ->
                    new IllegalArgumentException("Donation not found.")
            );

    LocalDateTime now = LocalDateTime.now();

    if (donation.getStatus() != DonationStatus.AVAILABLE) {
        throw new IllegalStateException(
                "This donation is no longer available."
        );
    }

    if (!donation.getExpiryTime().isAfter(now)
            || !donation.getPickupEnd().isAfter(now)) {

        throw new IllegalStateException(
                "This donation has expired or its pickup window has ended."
        );
    }

    boolean alreadyReserved =
            reservationRepository.existsByDonationAndStatus(
                    donation,
                    ReservationStatus.CONFIRMED
            );

    boolean awaitingConfirmation =
            reservationRepository.existsByDonationAndStatus(
                    donation,
                    ReservationStatus.PICKUP_PENDING_CONFIRMATION
            );

    if (alreadyReserved || awaitingConfirmation) {
        throw new IllegalStateException(
                "This donation already has an active reservation."
        );
    }

    Reservation reservation = new Reservation();

    reservation.setDonation(donation);
    reservation.setRecipient(recipient);
    reservation.setStatus(ReservationStatus.CONFIRMED);
    reservation.setFulfillmentMethod(fulfillmentMethod);

    if (fulfillmentMethod == FulfillmentMethod.DONOR_DELIVERY) {
        reservation.setDestinationAddress(destinationAddress.trim());
        reservation.setDestinationLatitude(destinationLatitude);
        reservation.setDestinationLongitude(destinationLongitude);
    }

    donation.setStatus(DonationStatus.RESERVED);

    return reservationRepository.save(reservation);
}


    // Retrieve reservation history for a recipient.
    @Transactional(readOnly = true)
    public List<Reservation> getMyReservations(String recipientEmail) {

        User recipient = findUserByEmail(recipientEmail);

        if (recipient.getRole() != Role.RECIPIENT) {
            throw new IllegalArgumentException(
                    "Only recipients can view their reservations."
            );
        }

        return reservationRepository
                .findByRecipientWithDonation(recipient);
    }

    // Retrieve reservations for the authenticated donor.
@Transactional(readOnly = true)
public List<Reservation> getDonorReservations(String donorEmail) {

    User donor = findUserByEmail(donorEmail);

    if (donor.getRole() != Role.DONOR) {
        throw new IllegalArgumentException(
                "Only donors can view their reservations."
        );
    }

    return reservationRepository
            .findByDonorWithDonationAndRecipient(donor);
}

    // Cancel a reservation.
    @Transactional
    public Reservation cancelReservation(
            String recipientEmail,
            Long reservationId) {

        User recipient = findUserByEmail(recipientEmail);

        if (recipient.getRole() != Role.RECIPIENT) {
            throw new IllegalArgumentException(
                    "Only recipients can cancel their reservations."
            );
        }

        if (reservationId == null) {
            throw new IllegalArgumentException(
                    "A valid reservation ID is required."
            );
        }

        // Read reservation to identify its donation.
        Reservation initialReservation = reservationRepository
                .findById(reservationId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Reservation not found.")
                );

        Long donationId = initialReservation.getDonation().getId();

        // Lock donation first.
        FoodDonation donation = donationRepository
                .findByIdForUpdate(donationId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Donation not found.")
                );

        // Lock reservation second.
        Reservation reservation = reservationRepository
                .findByIdForUpdate(reservationId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Reservation not found.")
                );

        if (!reservation.getDonation().getId().equals(donationId)) {
            throw new IllegalStateException(
                    "Reservation donation details have changed."
            );
        }

        if (!reservation.getRecipient().getId().equals(recipient.getId())) {
            throw new IllegalArgumentException(
                    "You cannot cancel another recipient's reservation."
            );
        }

        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new IllegalStateException(
                    "Only reservations awaiting pickup can be cancelled."
            );
        }

        LocalDateTime now = LocalDateTime.now();

        reservation.setStatus(ReservationStatus.CANCELLED);
        reservation.setCancelledAt(now);

        if (donation.getExpiryTime().isAfter(now)
                && donation.getPickupEnd().isAfter(now)) {

            donation.setStatus(DonationStatus.AVAILABLE);

        } else {
            donation.setStatus(DonationStatus.EXPIRED);
        }

        return reservationRepository.save(reservation);
    }

    // Record pickup confirmation from either party.
    @Transactional
    public Reservation confirmPickup(
            String userEmail,
            Long reservationId) {

        User user = findUserByEmail(userEmail);

        if (reservationId == null) {
            throw new IllegalArgumentException(
                    "A valid reservation ID is required."
            );
        }

        // Read reservation to identify its donation.
        Reservation initialReservation = reservationRepository
                .findById(reservationId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Reservation not found.")
                );

        Long donationId = initialReservation.getDonation().getId();

        // Lock donation first.
        FoodDonation donation = donationRepository
                .findByIdForUpdate(donationId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Donation not found.")
                );

        // Lock reservation second.
        Reservation reservation = reservationRepository
                .findByIdForUpdate(reservationId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Reservation not found.")
                );

        if (!reservation.getDonation().getId().equals(donationId)) {
            throw new IllegalStateException(
                    "Reservation donation details have changed."
            );
        }

        if (reservation.getStatus() != ReservationStatus.CONFIRMED
                && reservation.getStatus()
                != ReservationStatus.PICKUP_PENDING_CONFIRMATION) {

            throw new IllegalStateException(
                    "This reservation cannot receive pickup confirmation."
            );
        }

        if (donation.getStatus() != DonationStatus.RESERVED) {
            throw new IllegalStateException(
                    "This donation is not currently reserved."
            );
        }

        LocalDateTime now = LocalDateTime.now();

        if (!donation.getExpiryTime().isAfter(now)
                || !donation.getPickupEnd().isAfter(now)) {

            throw new IllegalStateException(
                    "Pickup confirmation is not allowed because the donation has expired or its pickup window has ended."
            );
        }

        if (user.getRole() == Role.RECIPIENT) {

            if (!reservation.getRecipient().getId().equals(user.getId())) {
                throw new IllegalArgumentException(
                        "You cannot confirm another recipient's pickup."
                );
            }

            if (reservation.isRecipientConfirmedPickup()) {
                throw new IllegalStateException(
                        "Recipient pickup confirmation has already been recorded."
                );
            }

            reservation.setRecipientConfirmedPickup(true);

        } else if (user.getRole() == Role.DONOR) {

            if (!donation.getDonor().getId().equals(user.getId())) {
                throw new IllegalArgumentException(
                        "You cannot confirm another donor's collection."
                );
            }

            if (reservation.isDonorConfirmedPickup()) {
                throw new IllegalStateException(
                        "Donor pickup confirmation has already been recorded."
                );
            }

            reservation.setDonorConfirmedPickup(true);

        } else {

            throw new IllegalArgumentException(
                    "Only the assigned donor and recipient can confirm pickup."
            );
        }

        boolean recipientConfirmed =
                reservation.isRecipientConfirmedPickup();

        boolean donorConfirmed =
                reservation.isDonorConfirmedPickup();

        if (recipientConfirmed && donorConfirmed) {

            reservation.setStatus(ReservationStatus.COMPLETED);
            reservation.setCompletedAt(now);

            donation.setStatus(DonationStatus.COLLECTED);

        } else {

            reservation.setStatus(
                    ReservationStatus.PICKUP_PENDING_CONFIRMATION
            );
        }

        return reservationRepository.save(reservation);
    }

    // Start delivery by the assigned donor.
    @Transactional
    public Reservation startDelivery(
            String donorEmail,
            Long reservationId) {

        User donor = findUserByEmail(donorEmail);

        if (donor.getRole() != Role.DONOR) {
            throw new IllegalArgumentException(
                    "Only donors can start delivery."
            );
        }

        Reservation reservation = reservationRepository
                .findByIdForUpdate(reservationId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Reservation not found.")
                );

        FoodDonation donation = reservation.getDonation();

        if (!donation.getDonor().getId().equals(donor.getId())) {
            throw new IllegalArgumentException(
                    "You cannot deliver another donor's reservation."
            );
        }

        if (reservation.getFulfillmentMethod()
                != FulfillmentMethod.DONOR_DELIVERY) {
            throw new IllegalStateException(
                    "This reservation is not a donor delivery."
            );
        }

        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new IllegalStateException(
                    "This reservation cannot start delivery."
            );
        }

        reservation.setStatus(ReservationStatus.DELIVERY_IN_PROGRESS);
        reservation.setDeliveryStartedAt(LocalDateTime.now());

        return reservationRepository.save(reservation);
    }

    // Save the donor's latest GPS location.
    @Transactional
    public Reservation updateDeliveryLocation(
            String donorEmail,
            Long reservationId,
            Double latitude,
            Double longitude) {

        User donor = findUserByEmail(donorEmail);

        if (donor.getRole() != Role.DONOR) {
            throw new IllegalArgumentException(
                    "Only donors can update delivery location."
            );
        }

        if (latitude == null || longitude == null
                || !Double.isFinite(latitude)
                || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90
                || longitude < -180 || longitude > 180) {

            throw new IllegalArgumentException(
                    "Valid GPS coordinates are required."
            );
        }

        Reservation reservation = reservationRepository
                .findByIdForUpdate(reservationId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Reservation not found.")
                );

        if (!reservation.getDonation().getDonor().getId()
                .equals(donor.getId())) {
            throw new IllegalArgumentException(
                    "You cannot update another donor's delivery location."
            );
        }

        if (reservation.getStatus()
                != ReservationStatus.DELIVERY_IN_PROGRESS) {
            throw new IllegalStateException(
                    "Delivery is not currently in progress."
            );
        }

        reservation.setDeliveryLatitude(latitude);
        reservation.setDeliveryLongitude(longitude);
        reservation.setLocationUpdatedAt(LocalDateTime.now());

        return reservationRepository.save(reservation);
    }

    // Confirm delivery by the donor or recipient.
    @Transactional
    public Reservation confirmDelivery(
            String userEmail,
            Long reservationId) {

        User user = findUserByEmail(userEmail);

        if (reservationId == null) {
            throw new IllegalArgumentException(
                    "A valid reservation ID is required."
            );
        }

        // Find reservation first to get donation ID.
        Reservation initialReservation = reservationRepository
                .findById(reservationId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Reservation not found.")
                );

        Long donationId = initialReservation.getDonation().getId();

        // Lock donation first.
        FoodDonation donation = donationRepository
                .findByIdForUpdate(donationId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Donation not found.")
                );

        // Lock reservation second.
        Reservation reservation = reservationRepository
                .findByIdForUpdate(reservationId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Reservation not found.")
                );

        if (reservation.getFulfillmentMethod()
                != FulfillmentMethod.DONOR_DELIVERY) {
            throw new IllegalStateException(
                    "This is not a donor delivery reservation."
            );
        }

        if (reservation.getStatus() != ReservationStatus.DELIVERY_IN_PROGRESS
                && reservation.getStatus()
                != ReservationStatus.DELIVERY_PENDING_CONFIRMATION) {
            throw new IllegalStateException(
                    "This delivery cannot be confirmed."
            );
        }

        LocalDateTime now = LocalDateTime.now();

        if (user.getRole() == Role.RECIPIENT) {

            if (!reservation.getRecipient().getId().equals(user.getId())) {
                throw new IllegalArgumentException(
                        "You cannot confirm another recipient's delivery."
                );
            }

            if (reservation.isRecipientConfirmedDelivery()) {
                throw new IllegalStateException(
                        "Recipient has already confirmed delivery."
                );
            }

            reservation.setRecipientConfirmedDelivery(true);

        } else if (user.getRole() == Role.DONOR) {

            if (!donation.getDonor().getId().equals(user.getId())) {
                throw new IllegalArgumentException(
                        "You cannot confirm another donor's delivery."
                );
            }

            if (reservation.isDonorConfirmedDelivery()) {
                throw new IllegalStateException(
                        "Donor has already confirmed delivery."
                );
            }

            reservation.setDonorConfirmedDelivery(true);

        } else {
            throw new IllegalArgumentException(
                    "Only the assigned donor and recipient can confirm delivery."
            );
        }

        // Complete only after both parties confirm.
        if (reservation.isRecipientConfirmedDelivery()
                && reservation.isDonorConfirmedDelivery()) {

            reservation.setStatus(ReservationStatus.COMPLETED);
            reservation.setCompletedAt(now);

            donation.setStatus(DonationStatus.COLLECTED);

        } else {
            reservation.setStatus(
                    ReservationStatus.DELIVERY_PENDING_CONFIRMATION
            );
        }

        return reservationRepository.save(reservation);
    }

     
    // Allow the recipient to view the donor's live delivery location.
    @Transactional(readOnly = true)
    public Reservation getDeliveryLocation(
            String recipientEmail,
            Long reservationId) {

        User recipient = findUserByEmail(recipientEmail);

        if (recipient.getRole() != Role.RECIPIENT) {
            throw new IllegalArgumentException(
                    "Only recipients can view delivery location."
            );
        }

        if (reservationId == null) {
            throw new IllegalArgumentException(
                    "A valid reservation ID is required."
            );
        }

        Reservation reservation = reservationRepository
                .findById(reservationId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Reservation not found.")
                );

        if (!reservation.getRecipient().getId()
                .equals(recipient.getId())) {
            throw new IllegalArgumentException(
                    "You cannot track another recipient's delivery."
            );
        }

        if (reservation.getFulfillmentMethod()
                != FulfillmentMethod.DONOR_DELIVERY) {
            throw new IllegalStateException(
                    "This reservation does not use donor delivery."
            );
        }

        if (reservation.getStatus()
                != ReservationStatus.DELIVERY_IN_PROGRESS
                && reservation.getStatus()
                != ReservationStatus.DELIVERY_PENDING_CONFIRMATION) {

            throw new IllegalStateException(
                    "Live tracking is not currently available."
            );
        }

        return reservation;
    }


    // Find a user using their authenticated email.
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

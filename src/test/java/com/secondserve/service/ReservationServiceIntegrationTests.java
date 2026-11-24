
package com.secondserve.service;

import com.secondserve.entity.*;
import com.secondserve.repository.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReservationServiceIntegrationTests {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FoodDonationRepository donationRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ReservationExpiryService reservationExpiryService;

    private User donor;
    private User recipient;
    private FoodDonation donation;

    @BeforeEach
    void setUp() {

        // Create donor
        donor = new User();
        donor.setFullName("Test Donor");
        donor.setEmail("donor@test.com");
        donor.setPasswordHash("test-hash");
        donor.setRole(Role.DONOR);

        donor = userRepository.saveAndFlush(donor);

        // Create recipient
        recipient = new User();
        recipient.setFullName("Test Recipient");
        recipient.setEmail("recipient@test.com");
        recipient.setPasswordHash("test-hash");
        recipient.setRole(Role.RECIPIENT);

        recipient = userRepository.saveAndFlush(recipient);

        // Create food donation
        LocalDateTime now = LocalDateTime.now();

        donation = new FoodDonation();
        donation.setDonor(donor);
        donation.setFoodName("Rice");
        donation.setFoodCategory("Grains");
        donation.setQuantity(5);
        donation.setUnit("kg");
        donation.setExpiryTime(now.plusHours(3));
        donation.setPickupStart(now.plusMinutes(10));
        donation.setPickupEnd(now.plusHours(2));
        donation.setPickupAddress("Test Address");
        donation.setFoodDescription("Fresh rice");
        donation.setStatus(DonationStatus.AVAILABLE);

        donation = donationRepository.saveAndFlush(donation);
    }

    
@Test
void recipientCanReserveAvailableDonation() {

    Reservation reservation =
            reservationService.createReservation(
                    recipient.getEmail(),
                    donation.getId()
            );

    // Check reservation was created
    assertNotNull(reservation.getId());

    // Check reservation status
    assertEquals(
            ReservationStatus.CONFIRMED,
            reservation.getStatus()
    );

    // Check donation status changed to RESERVED
    assertEquals(
            DonationStatus.RESERVED,
            donationRepository.findById(donation.getId())
                    .orElseThrow()
                    .getStatus()
    );
} // Close first test method


@Test
void sameDonationCannotBeReservedTwice() {

    // First reservation
    Reservation firstReservation =
            reservationService.createReservation(
                    recipient.getEmail(),
                    donation.getId()
            );

    assertNotNull(firstReservation.getId());

    // Try reserving the same donation again
    assertThrows(
            IllegalStateException.class,
            () -> reservationService.createReservation(
                    recipient.getEmail(),
                    donation.getId()
            )
    );
} // Close second test method

@Test
void recipientCanCancelReservation() {

    // First create a reservation
    Reservation reservation =
            reservationService.createReservation(
                    recipient.getEmail(),
                    donation.getId()
            );

    // Cancel the reservation
    Reservation cancelledReservation =
            reservationService.cancelReservation(
                    recipient.getEmail(),
                    reservation.getId()
            );

    // Check reservation status
    assertEquals(
            ReservationStatus.CANCELLED,
            cancelledReservation.getStatus()
    );

    // Check donation is available again
    assertEquals(
            DonationStatus.AVAILABLE,
            donationRepository.findById(donation.getId())
                    .orElseThrow()
                    .getStatus()
    );
}


@Test
void donationIsCollectedAfterBothConfirmPickup() {

    // Step 1: Create reservation
    Reservation reservation =
            reservationService.createReservation(
                    recipient.getEmail(),
                    donation.getId()
            );

    // Step 2: Recipient confirms pickup
    Reservation firstConfirmation =
            reservationService.confirmPickup(
                    recipient.getEmail(),
                    reservation.getId()
            );

    // Only one person has confirmed
    assertEquals(
            ReservationStatus.PICKUP_PENDING_CONFIRMATION,
            firstConfirmation.getStatus()
    );

    // Step 3: Donor confirms pickup
    Reservation finalConfirmation =
            reservationService.confirmPickup(
                    donor.getEmail(),
                    reservation.getId()
            );

    // Both have confirmed
    assertEquals(
            ReservationStatus.COMPLETED,
            finalConfirmation.getStatus()
    );

    // Step 4: Check donation status
    assertEquals(
            DonationStatus.COLLECTED,
            donationRepository.findById(donation.getId())
                    .orElseThrow()
                    .getStatus()
    );
}


@Test
void anotherRecipientCannotCancelReservation() {

    // Step 1: Create reservation for the original recipient
    Reservation reservation =
            reservationService.createReservation(
                    recipient.getEmail(),
                    donation.getId()
            );

    // Step 2: Create another recipient
    User anotherRecipient = new User();
    anotherRecipient.setFullName("Another Recipient");
    anotherRecipient.setEmail("another@test.com");
    anotherRecipient.setPasswordHash("test-hash");
    anotherRecipient.setRole(Role.RECIPIENT);

    User savedAnotherRecipient =
        userRepository.saveAndFlush(anotherRecipient);

    // Step 3: Another recipient tries to cancel the reservation
    assertThrows(
            IllegalArgumentException.class,
            () -> reservationService.cancelReservation(
                    anotherRecipient.getEmail(),
                    reservation.getId()
            )
    );

    // Step 4: Original reservation must remain confirmed
    assertEquals(
            ReservationStatus.CONFIRMED,
            reservationService.getMyReservations(recipient.getEmail())
                    .get(0)
                    .getStatus()
    );

    // Step 5: Donation must remain reserved
    assertEquals(
            DonationStatus.RESERVED,
            donationRepository.findById(donation.getId())
                    .orElseThrow()
                    .getStatus()
    );
}

@Test
void expiredDonationCannotBeReserved() {

    // Make the donation expired
    donation.setExpiryTime(LocalDateTime.now().minusHours(1));

    donationRepository.saveAndFlush(donation);

    // Attempt to reserve expired food
    assertThrows(
            IllegalStateException.class,
            () -> reservationService.createReservation(
                    recipient.getEmail(),
                    donation.getId()
            )
    );

    // Donation must remain available in the database
    assertEquals(
            DonationStatus.AVAILABLE,
            donationRepository.findById(donation.getId())
                    .orElseThrow()
                    .getStatus()
    );
}

@Test
void expiryServiceMarksAvailableDonationAsExpired() {

    // Make the available donation expired.
    donation.setExpiryTime(LocalDateTime.now().minusMinutes(1));

    donationRepository.saveAndFlush(donation);

    // Run the expiry service.
    reservationExpiryService.expireReservations();

    // Check donation status.
    assertEquals(
            DonationStatus.EXPIRED,
            donationRepository.findById(donation.getId())
                    .orElseThrow()
                    .getStatus()
    );
}


@Test
void expiryServiceMarksReservedDonationAndReservationAsExpired() {

    // First create a reservation.
    Reservation reservation =
            reservationService.createReservation(
                    recipient.getEmail(),
                    donation.getId()
            );

    // Make the donation expired.
    donation.setExpiryTime(LocalDateTime.now().minusMinutes(1));

    donationRepository.saveAndFlush(donation);

    // Run the expiry service.
    reservationExpiryService.expireReservations();

    // Check donation status.
    assertEquals(
            DonationStatus.EXPIRED,
            donationRepository.findById(donation.getId())
                    .orElseThrow()
                    .getStatus()
    );

    // Check reservation status.
    assertEquals(
            ReservationStatus.EXPIRED,
            reservationRepository.findById(reservation.getId())
                    .orElseThrow()
                    .getStatus()
    );
}


@Test
void expiryServiceDoesNotChangeCompletedReservation() {

    // Create a reservation.
    Reservation reservation =
            reservationService.createReservation(
                    recipient.getEmail(),
                    donation.getId()
            );

    // Mark reservation as completed for this test.
    reservation.setStatus(ReservationStatus.COMPLETED);

    reservationRepository.saveAndFlush(reservation);

    // Make donation expired.
    donation.setExpiryTime(LocalDateTime.now().minusMinutes(1));

    donationRepository.saveAndFlush(donation);

    // Run expiry service.
    reservationExpiryService.expireReservations();

    // Completed reservation must remain unchanged.
    assertEquals(
            ReservationStatus.COMPLETED,
            reservationRepository.findById(reservation.getId())
                    .orElseThrow()
                    .getStatus()
    );
}


@Test
void expiryServiceDoesNotChangeCancelledReservation() {

    // Create a reservation.
    Reservation reservation =
            reservationService.createReservation(
                    recipient.getEmail(),
                    donation.getId()
            );

    // Cancel the reservation.
    reservationService.cancelReservation(
            recipient.getEmail(),
            reservation.getId()
    );

    // Make donation expired.
    donation.setExpiryTime(LocalDateTime.now().minusMinutes(1));

    donationRepository.saveAndFlush(donation);

    // Run expiry service.
    reservationExpiryService.expireReservations();

    // Cancelled reservation must remain unchanged.
    assertEquals(
            ReservationStatus.CANCELLED,
            reservationRepository.findById(reservation.getId())
                    .orElseThrow()
                    .getStatus()
    );
}




} // Close class



   

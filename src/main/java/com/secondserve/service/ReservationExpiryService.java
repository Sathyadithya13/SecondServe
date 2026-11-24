
package com.secondserve.service;

import com.secondserve.entity.DonationStatus;
import com.secondserve.entity.FoodDonation;
import com.secondserve.entity.Reservation;
import com.secondserve.entity.ReservationStatus;

import com.secondserve.repository.FoodDonationRepository;
import com.secondserve.repository.ReservationRepository;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReservationExpiryService {

    private final FoodDonationRepository donationRepository;
    private final ReservationRepository reservationRepository;

    public ReservationExpiryService(
            FoodDonationRepository donationRepository,
            ReservationRepository reservationRepository) {

        this.donationRepository = donationRepository;
        this.reservationRepository = reservationRepository;
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void expireReservations() {

        LocalDateTime now = LocalDateTime.now();

        // Find only donations whose deadlines have passed.
        List<FoodDonation> donations =
                donationRepository.findDonationsPastDeadline(
                        List.of(
                                DonationStatus.AVAILABLE,
                                DonationStatus.RESERVED
                        ),
                        now
                );

        if (donations.isEmpty()) {
            return;
        }

        // Mark expired donations.
        for (FoodDonation donation : donations) {
            donation.setStatus(DonationStatus.EXPIRED);
        }

        // Find active reservations for these donations.
        List<Reservation> reservations =
                reservationRepository.findActiveReservations(
                        donations,
                        List.of(
                                ReservationStatus.CONFIRMED,
                                ReservationStatus.PICKUP_PENDING_CONFIRMATION
                        )
                );

        // Mark active reservations as expired.
        for (Reservation reservation : reservations) {
            reservation.setStatus(ReservationStatus.EXPIRED);
        }
    }
}

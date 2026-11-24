
package com.secondserve.repository;

import com.secondserve.entity.DonationStatus;
import com.secondserve.entity.FoodDonation;
import com.secondserve.entity.User;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import java.util.Optional;

public interface FoodDonationRepository
        extends JpaRepository<FoodDonation, Long> {

    // Find all donations created by a particular donor.
    List<FoodDonation> findByDonorOrderByCreatedAtDesc(User donor);

    // Find donations with a particular status.
    List<FoodDonation> findByStatusOrderByExpiryTimeAsc(
            DonationStatus status);

    // Find donations that are still available and have not expired.
    @Query("""
        SELECT d
        FROM FoodDonation d
        WHERE d.status = :status
          AND d.expiryTime > :now
          AND d.pickupEnd > :now
        ORDER BY d.expiryTime ASC
        """)
    List<FoodDonation> findAvailableDonations(
            @Param("status") DonationStatus status,
            @Param("now") LocalDateTime now);

    // Find available or reserved donations whose deadline has passed.
     @Query("""
        SELECT d
        FROM FoodDonation d
        WHERE d.status IN :statuses
        AND (d.expiryTime <= :now OR d.pickupEnd <= :now)
        """)
        List<FoodDonation> findDonationsPastDeadline(
                @Param("statuses") List<DonationStatus> statuses,
                @Param("now") LocalDateTime now);        

    // Count donations for a donor by status.
    long countByDonorAndStatus(User donor, DonationStatus status);

    // Count all donations created by a donor.
    long countByDonor(User donor);

   // Lock a donation row while processing a reservation.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM FoodDonation d WHERE d.id = :id")
    Optional<FoodDonation> findByIdForUpdate(
            @Param("id") Long id);
}

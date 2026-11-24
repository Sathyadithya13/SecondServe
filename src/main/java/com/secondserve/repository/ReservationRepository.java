
package com.secondserve.repository;

import com.secondserve.entity.FoodDonation;
import com.secondserve.entity.Reservation;
import com.secondserve.entity.ReservationStatus;
import com.secondserve.entity.User;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReservationRepository
        extends JpaRepository<Reservation, Long> {

    
    // Existing method
    List<Reservation> findByRecipientOrderByReservedAtDesc(User recipient);

    // NEW METHOD: Fetch reservations along with food donations
    @Query("""
        SELECT r
        FROM Reservation r
        JOIN FETCH r.donation
        WHERE r.recipient = :recipient
        ORDER BY r.reservedAt DESC
        """)
    List<Reservation> findByRecipientWithDonation(
            @Param("recipient") User recipient
    );

    // Fetch donor reservations along with donation and recipient details
@Query("""
    SELECT r
    FROM Reservation r
    JOIN FETCH r.donation d
    JOIN FETCH r.recipient
    WHERE d.donor = :donor
    ORDER BY r.reservedAt DESC
    """)
List<Reservation> findByDonorWithDonationAndRecipient(
        @Param("donor") User donor
);

    // Existing method
    @Query("""
        SELECT r
        FROM Reservation r
        WHERE r.donation IN :donations
        AND r.status IN :statuses
        """)
    List<Reservation> findActiveReservations(
            @Param("donations") List<FoodDonation> donations,
            @Param("statuses") List<ReservationStatus> statuses
    );

    boolean existsByDonationAndStatus(
            FoodDonation donation,
            ReservationStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reservation r WHERE r.id = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") Long id);
}
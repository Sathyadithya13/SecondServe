package com.secondserve.repository;

import com.secondserve.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByReservation_Id(Long reservationId);

    List<Payment> findByReservation_Recipient_EmailOrderByCreatedAtDesc(
            String email
    );
}
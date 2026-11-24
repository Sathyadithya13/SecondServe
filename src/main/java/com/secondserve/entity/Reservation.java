
package com.secondserve.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "reservations",
    indexes = {
        @Index(name = "idx_reservation_recipient", columnList = "recipient_id"),
        @Index(name = "idx_reservation_donation", columnList = "donation_id")
    }
)
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "donation_id", nullable = false)
    private FoodDonation donation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status = ReservationStatus.CONFIRMED;

    @Column(nullable = false, updatable = false)
    private LocalDateTime reservedAt;

    private LocalDateTime updatedAt;

    private LocalDateTime cancelledAt;

    private LocalDateTime completedAt;

    @Column(nullable = false)
    private boolean recipientConfirmedPickup = false;

    @Column(nullable = false)
    private boolean donorConfirmedPickup = false;
    // Delivery handover confirmations
@Column(nullable = false)
private boolean recipientConfirmedDelivery = false;

@Column(nullable = false)
private boolean donorConfirmedDelivery = false;
    // Fulfillment method
@Enumerated(EnumType.STRING)
@Column(name = "fulfillment_method", nullable = false, length = 30)
private FulfillmentMethod fulfillmentMethod = FulfillmentMethod.PICKUP;

// Recipient delivery destination
@Column(name = "destination_address", length = 500)
private String destinationAddress;

@Column(name = "destination_latitude")
private Double destinationLatitude;

@Column(name = "destination_longitude")
private Double destinationLongitude;

// Donor's latest delivery location
@Column(name = "delivery_latitude")
private Double deliveryLatitude;

@Column(name = "delivery_longitude")
private Double deliveryLongitude;

// Delivery timestamps
private LocalDateTime deliveryStartedAt;

private LocalDateTime locationUpdatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        reservedAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public FoodDonation getDonation() {
        return donation;
    }

    public void setDonation(FoodDonation donation) {
        this.donation = donation;
    }

    public User getRecipient() {
        return recipient;
    }

    public void setRecipient(User recipient) {
        this.recipient = recipient;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public void setStatus(ReservationStatus status) {
        this.status = status;
    }

    public LocalDateTime getReservedAt() {
        return reservedAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(LocalDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public boolean isRecipientConfirmedPickup() {
    return recipientConfirmedPickup;}

    public void setRecipientConfirmedPickup(boolean recipientConfirmedPickup) {
    this.recipientConfirmedPickup = recipientConfirmedPickup;}

    public boolean isDonorConfirmedPickup() {
    return donorConfirmedPickup;}

    public void setDonorConfirmedPickup(boolean donorConfirmedPickup) {
    this.donorConfirmedPickup = donorConfirmedPickup;}

    public FulfillmentMethod getFulfillmentMethod() {
    return fulfillmentMethod;
}

public void setFulfillmentMethod(FulfillmentMethod fulfillmentMethod) {
    this.fulfillmentMethod = fulfillmentMethod;
}

public String getDestinationAddress() {
    return destinationAddress;
}

public void setDestinationAddress(String destinationAddress) {
    this.destinationAddress = destinationAddress;
}

public Double getDestinationLatitude() {
    return destinationLatitude;
}

public void setDestinationLatitude(Double destinationLatitude) {
    this.destinationLatitude = destinationLatitude;
}

public Double getDestinationLongitude() {
    return destinationLongitude;
}

public void setDestinationLongitude(Double destinationLongitude) {
    this.destinationLongitude = destinationLongitude;
}

public Double getDeliveryLatitude() {
    return deliveryLatitude;
}

public void setDeliveryLatitude(Double deliveryLatitude) {
    this.deliveryLatitude = deliveryLatitude;
}

public Double getDeliveryLongitude() {
    return deliveryLongitude;
}

public void setDeliveryLongitude(Double deliveryLongitude) {
    this.deliveryLongitude = deliveryLongitude;
}

public LocalDateTime getDeliveryStartedAt() {
    return deliveryStartedAt;
}

public void setDeliveryStartedAt(LocalDateTime deliveryStartedAt) {
    this.deliveryStartedAt = deliveryStartedAt;
}

public LocalDateTime getLocationUpdatedAt() {
    return locationUpdatedAt;
}

public void setLocationUpdatedAt(LocalDateTime locationUpdatedAt) {
    this.locationUpdatedAt = locationUpdatedAt;
}

public boolean isRecipientConfirmedDelivery() {
    return recipientConfirmedDelivery;
}

public void setRecipientConfirmedDelivery(boolean recipientConfirmedDelivery) {
    this.recipientConfirmedDelivery = recipientConfirmedDelivery;
}

public boolean isDonorConfirmedDelivery() {
    return donorConfirmedDelivery;
}

public void setDonorConfirmedDelivery(boolean donorConfirmedDelivery) {
    this.donorConfirmedDelivery = donorConfirmedDelivery;
}
}

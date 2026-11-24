"use strict";

const reservationId = new URLSearchParams(window.location.search)
    .get("reservationId");

let csrfDetails = null;

const loadingMessage = document.getElementById("loadingMessage");
const checkoutContent = document.getElementById("checkoutContent");
const confirmButton = document.getElementById("confirmButton");
const message = document.getElementById("message");

let currentReservation = null;

async function readResponse(response) {
    const text = await response.text();

    if (!text) {
        return null;
    }

    try {
        return JSON.parse(text);
    } catch {
        return { message: text };
    }
}

async function loadCsrfToken() {
    const response = await fetch("/api/security/csrf", {
        credentials: "same-origin",
        headers: {
            Accept: "application/json"
        }
    });

    if (!response.ok) {
        throw new Error("Unable to load security token. Please sign in again.");
    }

    csrfDetails = await response.json();

    if (!csrfDetails.headerName || !csrfDetails.token) {
        throw new Error("Invalid security token.");
    }
}

function csrfHeaders() {
    return {
        [csrfDetails.headerName]: csrfDetails.token
    };
}

function showMessage(text, type) {
    message.textContent = text;
    message.className = `message ${type || ""}`;
}

async function loadReservation() {
    try {
        if (!reservationId) {
            throw new Error("Reservation ID is missing.");
        }

        const response = await fetch("/api/reservations/mine", {
            credentials: "same-origin",
            headers: {
                Accept: "application/json"
            }
        });

        const reservations = await readResponse(response);

        if (!response.ok) {
            throw new Error(
                reservations?.message || "Unable to load reservations."
            );
        }

        if (!Array.isArray(reservations)) {
            throw new Error("Invalid reservation response.");
        }

        currentReservation = reservations.find(
            item => String(item.reservationId) === String(reservationId)
        );

        if (!currentReservation) {
            throw new Error("Reservation not found in your account.");
        }

        if (currentReservation.status !== "CONFIRMED") {
            throw new Error(
                "Checkout is available only for confirmed reservations."
            );
        }

        const donation = currentReservation.donation || {};

        document.getElementById("foodName").textContent =
            donation.foodName || "Food donation";

        document.getElementById("quantity").textContent =
            `${donation.quantity ?? "N/A"} ${donation.unit || ""}`.trim();

        document.getElementById("reservationId").textContent =
            currentReservation.reservationId;

        loadingMessage.hidden = true;
        checkoutContent.hidden = false;

        await loadCsrfToken();

    } catch (error) {
        loadingMessage.textContent =
            error.message || "Unable to load checkout details.";

        loadingMessage.className = "message error";
        confirmButton.disabled = true;
    }
}

async function confirmFreeDonation() {
    if (!currentReservation) {
        showMessage("Reservation details are not available.", "error");
        return;
    }

    try {
        confirmButton.disabled = true;
        confirmButton.textContent = "Confirming...";

        if (!csrfDetails) {
            await loadCsrfToken();
        }

        const response = await fetch(
            `/api/payments/reservations/${reservationId}/free-confirmation`,
            {
                method: "POST",
                credentials: "same-origin",
                headers: {
                    Accept: "application/json",
                    ...csrfHeaders()
                }
            }
        );

        const data = await readResponse(response);

        if (response.status === 403) {
            await loadCsrfToken();
            throw new Error("Security token rejected. Please try again.");
        }

        if (!response.ok) {
            throw new Error(
                data?.message ||
                data?.error ||
                "Free checkout confirmation failed."
            );
        }

        showMessage(
            "Free donation confirmed successfully! Your transaction has been saved.",
            "success"
        );

        confirmButton.textContent = "Donation Confirmed";

        // Show the saved transaction page after a successful response.
        window.setTimeout(() => {
            window.location.href = "/transactions.html";
        }, 1500);

    } catch (error) {
        console.error("Free checkout error:", error);

        showMessage(
            error.message || "Unable to confirm free donation.",
            "error"
        );

        confirmButton.disabled = false;
        confirmButton.textContent = "Confirm Free Donation";
    }
}

confirmButton.addEventListener("click", confirmFreeDonation);

loadReservation();
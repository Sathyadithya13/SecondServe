"use strict";

const SecondServe = (() => {
    const LOGIN_URL = "/login.html";
    const CURRENT_USER_URL = "/api/auth/me";
    const CSRF_URL = "/api/security/csrf";
    const DONATIONS_URL = "/api/donations";

    let currentUser = null;
    let csrfDetails = null;
    let availableDonations = [];
    let activeDeliveryWatchId = null;

    // Recipient live delivery tracking
    let deliveryMap = null;
    let donorMarker = null;
    let destinationMarker = null;
    let trackingInterval = null;
    let trackedReservationId = null;

    // ==========================================
    // 1. SESSION INITIALIZATION
    // ==========================================

    async function checkSession() {
        try {
            const response = await fetch(CURRENT_USER_URL, {
                method: "GET",
                credentials: "same-origin",
                headers: { Accept: "application/json" }
            });

            if (response.status === 401 || response.status === 403) {
                window.location.replace(LOGIN_URL);
                return null;
            }

            if (!response.ok) {
                throw new Error("Unable to verify your login session.");
            }

            currentUser = await response.json();

            const requiredRole = document.body.dataset.requiredRole;

            if (requiredRole && currentUser.role !== requiredRole) {
                redirectToRoleDashboard(currentUser.role);
                return null;
            }

            displayUserDetails(currentUser);

            document.dispatchEvent(
                new CustomEvent("secondserve:ready", {
                    detail: currentUser
                })
            );

            await loadCsrfToken();

            if (currentUser.role === "DONOR") {
                await loadDonorDashboard();
                await loadDonorReservations();
                setupDonationForm();

            } else if (currentUser.role === "RECIPIENT") {
                await loadRecipientDashboard();
                await loadMyReservations();
                setupRecipientControls();
            }

            setupLogout();
            return currentUser;

        } catch (error) {
            console.error("Dashboard initialization failed:", error);

            showMessage(
                "session-message",
                error.message ||
                    "Unable to load your dashboard. Please refresh the page.",
                "error"
            );

            return null;
        }
    }

    function redirectToRoleDashboard(role) {
        const urls = {
            DONOR: "/donor-dashboard.html",
            RECIPIENT: "/recipient-dashboard.html"
        };

        if (urls[role]) {
            window.location.replace(urls[role]);
        } else {
            showMessage(
                "session-message",
                "This account does not have access to a public dashboard.",
                "error"
            );
        }
    }

    function displayUserDetails(user) {
        document.querySelectorAll("[data-user-name]").forEach(element => {
            element.textContent = user.fullName || "";
        });

        document.querySelectorAll("[data-user-email]").forEach(element => {
            element.textContent = user.email || "";
        });

        document.querySelectorAll("[data-user-role]").forEach(element => {
            element.textContent = user.role || "";
        });
    }

    // ==========================================
    // 2. COMMON UI HELPERS
    // ==========================================

    function showMessage(elementId, message, type = "info") {
        const element = document.getElementById(elementId);

        if (!element) {
            console.log(message);
            return;
        }

        element.textContent = message;
        element.className = `status-message visible ${type}`;
    }

    function setText(id, value) {
        const element = document.getElementById(id);

        if (element) {
            element.textContent =
                value === null || value === undefined
                    ? "0"
                    : String(value);
        }
    }

    function createElement(tag, className, text) {
        const element = document.createElement(tag);

        if (className) {
            element.className = className;
        }

        if (text !== undefined && text !== null) {
            element.textContent = String(text);
        }

        return element;
    }

    function addInfo(card, label, value) {
        const paragraph = document.createElement("p");

        const strong = document.createElement("strong");
        strong.textContent = `${label}: `;

        const span = document.createElement("span");
        span.textContent =
            value === null || value === undefined || value === ""
                ? "Not available"
                : String(value);

        paragraph.append(strong, span);
        card.appendChild(paragraph);
    }

    function createStatusElement(status) {
        const normalized = String(status || "UNKNOWN").toUpperCase();

        const labels = {
            AVAILABLE: "Available",
            CONFIRMED: "Reserved",
            PICKUP_PENDING_CONFIRMATION: "Pickup awaiting confirmation",
            DELIVERY_IN_PROGRESS: "Delivery in progress",
            DELIVERY_PENDING_CONFIRMATION: "Delivery awaiting confirmation",
            COMPLETED: "Completed",
            CANCELLED: "Cancelled",
            EXPIRED: "Expired",
            PENDING: "Pending"
        };

        const classNames = {
            AVAILABLE: "status-available",
            CONFIRMED: "status-reserved",
            PICKUP_PENDING_CONFIRMATION: "status-pending",
            DELIVERY_IN_PROGRESS: "status-pending",
            DELIVERY_PENDING_CONFIRMATION: "status-pending",
            COMPLETED: "status-completed",
            CANCELLED: "status-cancelled",
            EXPIRED: "status-expired",
            PENDING: "status-pending"
        };

        const element = createElement(
            "span",
            `pill ${classNames[normalized] || ""}`,
            labels[normalized] || normalized.replaceAll("_", " ")
        );

        return element;
    }

    function appendStatus(card, status) {
        const paragraph = document.createElement("p");
        const label = document.createElement("strong");

        label.textContent = "Status: ";

        paragraph.append(label, createStatusElement(status));
        card.appendChild(paragraph);
    }

    function formatDate(value) {
        if (!value) {
            return "Not available";
        }

        const date = new Date(value);

        if (Number.isNaN(date.getTime())) {
            return value;
        }

        return date.toLocaleString();
    }

    function toLocalDateTime(value) {
        if (!value) {
            return "";
        }

        return value.length >= 16 ? value.slice(0, 16) : value;
    }

    function isExpired(expiryTime) {
        if (!expiryTime) {
            return false;
        }

        const expiry = new Date(expiryTime);

        if (Number.isNaN(expiry.getTime())) {
            return false;
        }

        return expiry.getTime() <= Date.now();
    }

    // ==========================================
    // 3. CSRF SECURITY
    // ==========================================

    async function loadCsrfToken() {
        const response = await fetch(CSRF_URL, {
            method: "GET",
            credentials: "same-origin",
            headers: { Accept: "application/json" }
        });

        if (!response.ok) {
            throw new Error(
                "Could not load the security token. Please refresh the page."
            );
        }

        csrfDetails = await response.json();

        if (!csrfDetails?.headerName || !csrfDetails?.token) {
            throw new Error("The server returned an invalid security token.");
        }
    }

    async function refreshCsrfToken() {
        csrfDetails = null;
        await loadCsrfToken();
    }

    function csrfHeaders() {
        if (!csrfDetails) {
            throw new Error("Security token is missing. Refresh the page.");
        }

        return {
            [csrfDetails.headerName]: csrfDetails.token
        };
    }

    // ==========================================
    // 4. RESPONSE HELPER
    // ==========================================

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

    // ==========================================
    // 5. DONOR DASHBOARD
    // ==========================================

    async function loadDonorDashboard() {
        const results = await Promise.allSettled([
            fetch(`${DONATIONS_URL}/stats`, {
                credentials: "same-origin",
                headers: { Accept: "application/json" }
            }),

            fetch(`${DONATIONS_URL}/mine`, {
                credentials: "same-origin",
                headers: { Accept: "application/json" }
            })
        ]);

        // Donation statistics
        if (results[0].status === "fulfilled") {
            const response = results[0].value;
            const stats = await readResponse(response);

            if (response.ok && stats) {
                setText("totalDonations", stats.totalDonations);
                setText("availableDonations", stats.availableDonations);

                if (stats.completedPickups !== undefined) {
                    setText("completedPickups", stats.completedPickups);
                }
            } else {
                showMessage(
                    "session-message",
                    stats?.message || "Could not load donation statistics.",
                    "error"
                );
            }
        } else {
            console.error("Stats request failed:", results[0].reason);
        }

        // Donation history
        if (results[1].status === "fulfilled") {
            const response = results[1].value;
            const donations = await readResponse(response);

            if (response.ok && Array.isArray(donations)) {
                renderDonations(donations);
            } else {
                showMessage(
                    "session-message",
                    donations?.message || "Could not load donation history.",
                    "error"
                );
            }
        } else {
            console.error(
                "Donation history request failed:",
                results[1].reason
            );
        }
    }

    function renderDonations(donations) {
        const container = document.getElementById("donationList");

        if (!container) {
            return;
        }

        container.replaceChildren();

        if (donations.length === 0) {
            const empty = createElement("div", "empty-state");

            empty.append(
                createElement("strong", "", "No donations yet"),
                createElement(
                    "p",
                    "",
                    "Your saved donations will appear here after you create one."
                )
            );

            container.appendChild(empty);
            return;
        }

        donations.forEach(donation => {
            const card = createElement("article", "donation-card");

            card.appendChild(
                createElement("h3", "", donation.foodName || "Food donation")
            );

            addInfo(
                card,
                "Quantity",
                `${donation.quantity ?? "N/A"} ${donation.unit || ""}`.trim()
            );

            appendStatus(card, donation.status);

            addInfo(card, "Category", donation.foodCategory);
            addInfo(card, "Expiry", formatDate(donation.expiryTime));
            addInfo(card, "Pickup address", donation.pickupAddress);

            if (donation.foodDescription) {
                addInfo(card, "Description", donation.foodDescription);
            }

            container.appendChild(card);
        });
    }
        // ==========================================
    // 6. DONOR PICKUP MANAGEMENT
    // ==========================================

    async function loadDonorReservations() {
        const pickupList = document.getElementById("donorPickupList");

        if (!pickupList) {
            return;
        }

        pickupList.replaceChildren();

        pickupList.appendChild(
            createElement("p", "", "Loading donor pickups...")
        );

        try {
            const response = await fetch("/api/reservations/donor", {
                method: "GET",
                credentials: "same-origin",
                headers: { Accept: "application/json" }
            });

            const data = await readResponse(response);

            if (!response.ok) {
                throw new Error(
                    data?.message ||
                    data?.error ||
                    `Could not load donor pickups (HTTP ${response.status}).`
                );
            }

            if (!Array.isArray(data)) {
                throw new Error(
                    "The server returned an unexpected pickup response."
                );
            }

            renderDonorReservations(data);

        } catch (error) {
            console.error("Loading donor pickups failed:", error);

            pickupList.replaceChildren();

            pickupList.appendChild(
                createElement(
                    "p",
                    "",
                    error.message || "Unable to load donor pickups."
                )
            );

            showMessage(
                "donorPickupMessage",
                error.message || "Unable to load donor pickups.",
                "error"
            );
        }
    }

    function renderDonorReservations(reservations) {
        const pickupList = document.getElementById("donorPickupList");
        if (!pickupList) return;

        pickupList.replaceChildren();

        const completedCount = reservations.filter(
            reservation => reservation.status === "COMPLETED"
        ).length;
        setText("completedPickups", completedCount);

        if (reservations.length === 0) {
            const empty = createElement("div", "empty-state");
            empty.append(
                createElement("strong", "", "No reservations yet"),
                createElement("p", "", "When someone reserves your food, the reservation details will appear here.")
            );
            pickupList.appendChild(empty);
            return;
        }

        reservations.forEach(reservation => {
            const donation = reservation.donation || {};
            const recipient = reservation.recipient || {};
            const isDelivery = reservation.fulfillmentMethod === "DONOR_DELIVERY";
            const card = createElement("article", "donation-card");

            card.appendChild(createElement("h3", "", donation.foodName || "Food reservation"));
            addInfo(card, "Quantity", `${donation.quantity ?? "N/A"} ${donation.unit || ""}`.trim());
            appendStatus(card, reservation.status);
            addInfo(card, "Recipient", recipient.recipientName);
            addInfo(card, "Recipient email", recipient.recipientEmail);
            addInfo(
                card,
                isDelivery ? "Delivery destination" : "Pickup address",
                isDelivery ? reservation.destinationAddress : donation.pickupAddress
            );
            addInfo(card, "Pickup time", `${formatDate(donation.pickupStart)} to ${formatDate(donation.pickupEnd)}`);

            // Normal pickup confirmation
            if (!isDelivery) {
                addInfo(card, "Your confirmation",
                    reservation.donorConfirmedPickup ? "Confirmed" : "Not confirmed");

                addInfo(card, "Recipient confirmation",
                    reservation.recipientConfirmedPickup ? "Confirmed" : "Not confirmed");

                if (
                    ["CONFIRMED", "PICKUP_PENDING_CONFIRMATION"].includes(reservation.status) &&
                    !reservation.donorConfirmedPickup
                ) {
                    const button = createElement("button", "btn btn-primary", "Confirm Pickup");
                    button.type = "button";
                    button.addEventListener("click", () => {
                        confirmDonorPickup(reservation.reservationId, button);
                    });
                    card.appendChild(button);
                }
            }

            // Start donor delivery
            if (isDelivery && reservation.status === "CONFIRMED") {
                const startButton = createElement(
                    "button",
                    "btn btn-primary",
                    "Start Delivery & Share GPS"
                );

                startButton.type = "button";
                startButton.addEventListener("click", () => {
                    startDonorDelivery(reservation.reservationId, startButton);
                });

                card.appendChild(startButton);
            }

            if (isDelivery && reservation.status === "DELIVERY_IN_PROGRESS") {
    addInfo(card, "Delivery", "Live GPS tracking is active");

    if (!reservation.donorConfirmedDelivery) {
        const button = createElement(
            "button",
            "btn btn-primary",
            "Confirm Delivery"
        );

        button.type = "button";

        button.addEventListener("click", () =>
            confirmDelivery(
                reservation.reservationId,
                button,
                "donorPickupMessage"
            )
        );

        card.appendChild(button);
    }
}

if (isDelivery && reservation.status === "DELIVERY_PENDING_CONFIRMATION") {
    addInfo(
        card,
        "Your delivery confirmation",
        reservation.donorConfirmedDelivery ? "Confirmed" : "Not confirmed"
    );

    addInfo(
        card,
        "Recipient delivery confirmation",
        reservation.recipientConfirmedDelivery ? "Confirmed" : "Not confirmed"
    );

    if (!reservation.donorConfirmedDelivery) {
        const button = createElement(
            "button",
            "btn btn-primary",
            "Confirm Delivery"
        );

        button.type = "button";

        button.addEventListener("click", () =>
            confirmDelivery(
                reservation.reservationId,
                button,
                "donorPickupMessage"
            )
        );

        card.appendChild(button);
    }
}

            pickupList.appendChild(card);
        });
  } 

    async function confirmDonorPickup(reservationId, button) {
        try {
            button.disabled = true;
            button.textContent = "Confirming...";

            if (!csrfDetails) {
                await refreshCsrfToken();
            }

            const response = await fetch(
                `/api/reservations/${reservationId}/confirm-pickup`,
                {
                    method: "PATCH",
                    credentials: "same-origin",
                    headers: {
                        Accept: "application/json",
                        ...csrfHeaders()
                    }
                }
            );

            const data = await readResponse(response);

            if (response.status === 403) {
                await refreshCsrfToken();
                throw new Error("Security token rejected. Please try again.");
            }

            if (!response.ok) {
                throw new Error(
                    data?.message ||
                    data?.error ||
                    `Pickup confirmation failed (HTTP ${response.status}).`
                );
            }

            showMessage(
                "donorPickupMessage",
                "Your pickup confirmation was saved successfully.",
                "success"
            );

            await refreshCsrfToken();
            await loadDonorReservations();
            await loadDonorDashboard();

        } catch (error) {
            console.error("Donor pickup confirmation failed:", error);

            showMessage(
                "donorPickupMessage",
                error.message || "Unable to confirm pickup.",
                "error"
            );

            button.disabled = false;
            button.textContent = "Confirm Pickup";
        }
    }

    // Start delivery only after the donor grants GPS permission.
    async function startDonorDelivery(reservationId, button) {
        if (!navigator.geolocation) {
            showMessage(
                "donorPickupMessage",
                "Your browser does not support GPS location.",
                "error"
            );
            return;
        }

        button.disabled = true;
        button.textContent = "Requesting GPS...";

        try {
            const position = await new Promise((resolve, reject) => {
                navigator.geolocation.getCurrentPosition(
                    resolve,
                    reject,
                    {
                        enableHighAccuracy: true,
                        maximumAge: 0,
                        timeout: 20000
                    }
                );
            });

            if (!csrfDetails) {
                await refreshCsrfToken();
            }

            const response = await fetch(
                `/api/reservations/${reservationId}/start-delivery`,
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

            if (!response.ok) {
                throw new Error(
                    data?.message || data?.error || "Could not start delivery."
                );
            }

            // Send the first GPS location
            await sendDeliveryLocation(reservationId, position);

            if (activeDeliveryWatchId !== null) {
                navigator.geolocation.clearWatch(activeDeliveryWatchId);
            }

            // Continue sharing GPS updates
            activeDeliveryWatchId = navigator.geolocation.watchPosition(
                currentPosition => {
                    sendDeliveryLocation(reservationId, currentPosition)
                        .catch(error => {
                            console.error("GPS update failed:", error);
                        });
                },
                error => {
                    console.error("GPS permission/location error:", error);

                    showMessage(
                        "donorPickupMessage",
                        "GPS unavailable. Check your browser location permission.",
                        "error"
                    );
                },
                {
                    enableHighAccuracy: true,
                    maximumAge: 5000,
                    timeout: 15000
                }
            );

            showMessage(
                "donorPickupMessage",
                "Delivery started. Live GPS sharing is active.",
                "success"
            );

            button.textContent = "Delivery Started";
            await loadDonorReservations();

        } catch (error) {
            console.error("Starting delivery failed:", error);

            const message = error.code === 1
                ? "GPS permission denied. Allow location access and try again."
                : error.message || "Unable to start delivery.";

            showMessage("donorPickupMessage", message, "error");

            button.disabled = false;
            button.textContent = "Start Delivery & Share GPS";
        }
    }

    async function sendDeliveryLocation(reservationId, position) {
        if (!csrfDetails) {
            await refreshCsrfToken();
        }

        const response = await fetch(
            `/api/reservations/${reservationId}/delivery-location`,
            {
                method: "PATCH",
                credentials: "same-origin",
                headers: {
                    "Content-Type": "application/json",
                    Accept: "application/json",
                    ...csrfHeaders()
                },
                body: JSON.stringify({
                    latitude: position.coords.latitude,
                    longitude: position.coords.longitude
                })
            }
        );

        const data = await readResponse(response);

        if (!response.ok) {
            if (response.status === 403) {
                await refreshCsrfToken();
            }

            throw new Error(
                data?.message || data?.error || "Could not update GPS location."
            );
        }
    }

    // ==========================================
    // 7. RECIPIENT: AVAILABLE FOOD
    // ==========================================

    async function loadRecipientDashboard() {
        const foodList = document.getElementById("foodList");

        if (!foodList) {
            return;
        }

        foodList.replaceChildren();

        foodList.appendChild(
            createElement("p", "", "Loading available food...")
        );

        try {
            const response = await fetch(`${DONATIONS_URL}/available`, {
                method: "GET",
                credentials: "same-origin",
                headers: { Accept: "application/json" }
            });

            const data = await readResponse(response);

            if (!response.ok) {
                throw new Error(
                    data?.message ||
                    `Could not load food listings (HTTP ${response.status}).`
                );
            }

            if (!Array.isArray(data)) {
                throw new Error(
                    "The server returned an unexpected food-list response."
                );
            }

            availableDonations = data;
            setText("availableCount", data.length);
            renderAvailableFood();

            showMessage(
                "food-message",
                data.length
                    ? `Loaded ${data.length} available food donation(s).`
                    : "No food is currently available for collection.",
                "info"
            );

        } catch (error) {
            console.error("Loading available food failed:", error);

            foodList.replaceChildren();

            const empty = createElement("div", "empty-state");

            empty.append(
                createElement("strong", "", "Unable to load food listings"),
                createElement("p", "", "Please try refreshing the listings.")
            );

            foodList.appendChild(empty);

            showMessage(
                "food-message",
                error.message || "Unable to load available food.",
                "error"
            );
        }
    }
        function renderAvailableFood() {
        const foodList = document.getElementById("foodList");
        const searchInput = document.getElementById("searchFood");
        const categorySelect = document.getElementById("filterCategory");

        if (!foodList) return;

        const searchText = searchInput
            ? searchInput.value.trim().toLowerCase()
            : "";

        const selectedCategory = categorySelect
            ? categorySelect.value
            : "";

        const filtered = availableDonations.filter(donation => {
            const matchesName = (donation.foodName || "")
                .toLowerCase()
                .includes(searchText);

            const matchesCategory =
                !selectedCategory ||
                donation.foodCategory === selectedCategory;

            return matchesName && matchesCategory;
        });

        foodList.replaceChildren();

        if (filtered.length === 0) {
            const empty = createElement("div", "empty-state");

            empty.append(
                createElement(
                    "strong",
                    "",
                    availableDonations.length === 0
                        ? "No available food yet"
                        : "No matching food found"
                ),
                createElement(
                    "p",
                    "",
                    availableDonations.length === 0
                        ? "Please check again later."
                        : "Try another food name or category."
                )
            );

            foodList.appendChild(empty);
            return;
        }

        filtered.forEach(donation => {
            const card = createElement("article", "donation-card");

            card.appendChild(
                createElement("h3", "", donation.foodName || "Food donation")
            );

            addInfo(card, "Category", donation.foodCategory);

            addInfo(
                card,
                "Quantity",
                `${donation.quantity ?? "N/A"} ${donation.unit || ""}`.trim()
            );

            appendStatus(card, donation.status || "AVAILABLE");
            addInfo(card, "Expiry", formatDate(donation.expiryTime));
            addInfo(card, "Pickup from", formatDate(donation.pickupStart));
            addInfo(card, "Pickup until", formatDate(donation.pickupEnd));
            addInfo(card, "Pickup address", donation.pickupAddress);

            if (donation.foodDescription) {
                addInfo(card, "Food details", donation.foodDescription);
            }

            // Fulfillment method selection
            const methodSelect = document.createElement("select");
            methodSelect.className = "fulfillment-select";

            methodSelect.add(new Option("Pickup", "PICKUP"));
            methodSelect.add(new Option("Donor Delivery", "DONOR_DELIVERY"));

            // Recipient delivery address
            const addressInput = document.createElement("input");
            addressInput.type = "text";
            addressInput.placeholder = "Enter your delivery address";
            addressInput.className = "destination-input";
            addressInput.style.display = "none";

            // Recipient destination GPS
            const locationButton = createElement(
                "button",
                "btn btn-secondary",
                "Use My Current Location"
            );

            locationButton.type = "button";
            locationButton.style.display = "none";

            const locationStatus = createElement("p", "", "");

            let destinationLatitude = null;
            let destinationLongitude = null;

            methodSelect.addEventListener("change", () => {
                const isDelivery =
                    methodSelect.value === "DONOR_DELIVERY";

                addressInput.style.display =
                    isDelivery ? "block" : "none";

                locationButton.style.display =
                    isDelivery ? "inline-block" : "none";

                locationStatus.textContent = "";
            });

            locationButton.addEventListener("click", () => {
                if (!navigator.geolocation) {
                    locationStatus.textContent =
                        "GPS is not supported by this browser.";
                    return;
                }

                locationButton.disabled = true;
                locationStatus.textContent = "Getting your location...";

                navigator.geolocation.getCurrentPosition(
                    position => {
                        destinationLatitude = position.coords.latitude;
                        destinationLongitude = position.coords.longitude;

                        locationStatus.textContent =
                            "Destination location captured.";

                        locationButton.disabled = false;
                    },
                    error => {
                        locationStatus.textContent =
                            error.code === 1
                                ? "Location permission denied. Allow location access."
                                : "Could not get your location. Try again.";

                        locationButton.disabled = false;
                    },
                    {
                        enableHighAccuracy: true,
                        maximumAge: 0,
                        timeout: 15000
                    }
                );
            });

            card.append(
                createElement("p", "", "Choose fulfillment method:"),
                methodSelect,
                addressInput,
                locationButton,
                locationStatus
            );

            const button = createElement(
                "button",
                "reserve-button",
                "Reserve Food"
            );

            button.type = "button";

            const unavailable =
                donation.status &&
                donation.status !== "AVAILABLE";

            const expired = isExpired(donation.expiryTime);

            if (unavailable || expired) {
                button.disabled = true;
                button.textContent = expired
                    ? "Expired"
                    : "Not Available";
            } else {
                button.addEventListener("click", async () => {
                    if (methodSelect.value === "DONOR_DELIVERY") {
                        const destinationAddress =
                            addressInput.value.trim();

                        if (!destinationAddress) {
                            showMessage(
                                "food-message",
                                "Please enter your delivery address.",
                                "error"
                            );
                            return;
                        }

                        if (
                            destinationLatitude === null ||
                            destinationLongitude === null
                        ) {
                            showMessage(
                                "food-message",
                                "Please capture your destination GPS location.",
                                "error"
                            );
                            return;
                        }

                        await reserveFood(donation.id, button, {
                            destinationAddress,
                            destinationLatitude,
                            destinationLongitude
                        });

                    } else {
                        await reserveFood(donation.id, button);
                    }
                });
            }

            card.appendChild(button);
            foodList.appendChild(card);
        });
    }

    // ==========================================
    // 8. RECIPIENT: RESERVE FOOD
    // ==========================================

    async function reserveFood(donationId, button, deliveryDetails = null) {
        try {
            button.disabled = true;
            button.textContent = "Reserving...";

            if (!csrfDetails) {
                await refreshCsrfToken();
            }

            const requestBody = {
                donationId: donationId,
                fulfillmentMethod: deliveryDetails
                    ? "DONOR_DELIVERY"
                    : "PICKUP"
            };

            // Add destination details only for donor delivery.
            if (deliveryDetails) {
                requestBody.destinationAddress =
                    deliveryDetails.destinationAddress;

                requestBody.destinationLatitude =
                    deliveryDetails.destinationLatitude;

                requestBody.destinationLongitude =
                    deliveryDetails.destinationLongitude;
            }

            const response = await fetch("/api/reservations", {
                method: "POST",
                credentials: "same-origin",
                headers: {
                    "Content-Type": "application/json",
                    Accept: "application/json",
                    ...csrfHeaders()
                },
                body: JSON.stringify(requestBody)
            });

            const data = await readResponse(response);

            if (response.status === 403) {
                await refreshCsrfToken();

                throw new Error(
                    "Security token rejected. Please try again."
                );
            }

            if (!response.ok) {
                throw new Error(
                    data?.message ||
                    data?.error ||
                    `Reservation failed (HTTP ${response.status}).`
                );
            }

            if (!data || !data.reservationId) {
                throw new Error(
                    "Reservation created, but reservation ID was not returned."
                );
            }

            showMessage(
                "food-message",
                "Food reserved successfully! Redirecting to checkout...",
                "success"
            );

            window.location.href =
                `/payment.html?reservationId=${data.reservationId}`;

        } catch (error) {
            console.error("Reservation failed:", error);

            showMessage(
                "food-message",
                error.message || "Unable to reserve food.",
                "error"
            );

            button.disabled = false;
            button.textContent = "Reserve Food";
        }
    }

    // ==========================================
    // 9. RECIPIENT: MY RESERVATIONS
    // ==========================================

    async function loadMyReservations() {
        const reservationList = document.getElementById("reservationList");

        if (!reservationList) {
            return;
        }

        reservationList.replaceChildren();

        reservationList.appendChild(
            createElement("p", "", "Loading your reservations...")
        );

        try {
            const response = await fetch("/api/reservations/mine", {
                method: "GET",
                credentials: "same-origin",
                headers: {
                    Accept: "application/json"
                }
            });

            const data = await readResponse(response);

            if (!response.ok) {
                throw new Error(
                    data?.message || "Could not load your reservations."
                );
            }

            if (!Array.isArray(data)) {
                throw new Error("Unexpected reservation response.");
            }

            renderMyReservations(data);

        } catch (error) {
            console.error("Loading reservations failed:", error);

            reservationList.replaceChildren();

            reservationList.appendChild(
                createElement(
                    "p",
                    "",
                    error.message || "Unable to load reservations."
                )
            );
        }
    }

    async function confirmRecipientPickup(reservationId, button) {
        try {
            button.disabled = true;
            button.textContent = "Confirming...";

            if (!csrfDetails) {
                await refreshCsrfToken();
            }

            const response = await fetch(
                `/api/reservations/${reservationId}/confirm-pickup`,
                {
                    method: "PATCH",
                    credentials: "same-origin",
                    headers: {
                        Accept: "application/json",
                        ...csrfHeaders()
                    }
                }
            );

            const data = await readResponse(response);

            if (response.status === 403) {
                await refreshCsrfToken();

                throw new Error(
                    "Security token rejected. Please try again."
                );
            }

            if (!response.ok) {
                throw new Error(
                    data?.message ||
                    data?.error ||
                    `Pickup confirmation failed (HTTP ${response.status}).`
                );
            }

            showMessage(
                "session-message",
                data?.status === "COMPLETED"
                    ? "Pickup completed successfully! Thank you for rescuing food."
                    : "Your pickup confirmation was recorded. Waiting for donor confirmation.",
                "success"
            );

            await loadMyReservations();

        } catch (error) {
            console.error(
                "Recipient pickup confirmation failed:",
                error
            );

            showMessage(
                "session-message",
                error.message || "Unable to confirm pickup.",
                "error"
            );

            button.disabled = false;
            button.textContent = "Confirm Pickup";
        }
    }

    // Confirm delivery by donor or recipient
    async function confirmDelivery(
        reservationId,
        button,
        messageElementId = "session-message"
    ) {
        try {
            button.disabled = true;
            button.textContent = "Confirming...";

            if (!csrfDetails) {
                await refreshCsrfToken();
            }

            const response = await fetch(
                `/api/reservations/${reservationId}/confirm-delivery`,
                {
                    method: "PATCH",
                    credentials: "same-origin",
                    headers: {
                        Accept: "application/json",
                        ...csrfHeaders()
                    }
                }
            );

            const data = await readResponse(response);

            if (!response.ok) {
                throw new Error(
                    data?.message ||
                    data?.error ||
                    "Delivery confirmation failed."
                );
            }

            showMessage(
                messageElementId,
                data?.status === "COMPLETED"
                    ? "Delivery completed successfully!"
                    : "Your confirmation was saved. Waiting for the other party.",
                "success"
            );

            await refreshCsrfToken();

            if (currentUser?.role === "DONOR") {
                await loadDonorReservations();
                await loadDonorDashboard();
            } else {
                await loadMyReservations();
            }

        } catch (error) {
            console.error("Delivery confirmation failed:", error);

            showMessage(
                messageElementId,
                error.message || "Unable to confirm delivery.",
                "error"
            );

            button.disabled = false;
            button.textContent = "Confirm Delivery";
        }
    }

    function renderMyReservations(reservations) {
        const reservationList = document.getElementById("reservationList");

        if (!reservationList) return;

        reservationList.replaceChildren();

        setText("reservationCount", reservations.length);

        setText(
            "pickupCount",
            reservations.filter(r => r.status === "COMPLETED").length
        );

        if (reservations.length === 0) {
            reservationList.appendChild(
                createElement("p", "", "You have not reserved any food yet.")
            );
            return;
        }

        reservations.forEach(reservation => {
            const donation = reservation.donation || {};
            const isDelivery =
                reservation.fulfillmentMethod === "DONOR_DELIVERY";

            const card = createElement("article", "donation-card");

            card.appendChild(
                createElement("h3", "", donation.foodName || "Food reservation")
            );

            addInfo(
                card,
                "Quantity",
                `${donation.quantity ?? "N/A"} ${donation.unit || ""}`.trim()
            );

            appendStatus(card, reservation.status);

            addInfo(
                card,
                isDelivery ? "Delivery destination" : "Pickup address",
                isDelivery
                    ? reservation.destinationAddress
                    : donation.pickupAddress
            );

            addInfo(
                card,
                "Pickup time",
                `${formatDate(donation.pickupStart)} to ${formatDate(donation.pickupEnd)}`
            );

            // Normal pickup confirmation only
            if (!isDelivery) {
                addInfo(
                    card,
                    "Donor confirmation",
                    reservation.donorConfirmedPickup ? "Confirmed" : "Not confirmed"
                );

                addInfo(
                    card,
                    "Recipient confirmation",
                    reservation.recipientConfirmedPickup ? "Confirmed" : "Not confirmed"
                );

                if (
                    ["CONFIRMED", "PICKUP_PENDING_CONFIRMATION"].includes(reservation.status) &&
                    !reservation.recipientConfirmedPickup
                ) {
                    const confirmButton = createElement(
                        "button",
                        "btn btn-primary",
                        "Confirm Pickup"
                    );

                    confirmButton.type = "button";

                    confirmButton.addEventListener("click", () => {
                        confirmRecipientPickup(
                            reservation.reservationId,
                            confirmButton
                        );
                    });

                    card.appendChild(confirmButton);
                }
            }

            // Recipient can track active donor delivery
            if (
                isDelivery &&
                ["DELIVERY_IN_PROGRESS", "DELIVERY_PENDING_CONFIRMATION"].includes(reservation.status)
            ) {
                const trackButton = createElement(
                    "button",
                    "btn btn-primary",
                    "Track Live Delivery"
                );

                trackButton.type = "button";

                trackButton.addEventListener("click", () => {
                    startRecipientTracking(reservation.reservationId);
                });

                card.appendChild(trackButton);
            }

            // Recipient confirms delivery
            if (
                isDelivery &&
                reservation.status === "DELIVERY_PENDING_CONFIRMATION"
            ) {
                addInfo(
                    card,
                    "Donor delivery confirmation",
                    reservation.donorConfirmedDelivery ? "Confirmed" : "Not confirmed"
                );

                addInfo(
                    card,
                    "Your delivery confirmation",
                    reservation.recipientConfirmedDelivery ? "Confirmed" : "Not confirmed"
                );

                if (!reservation.recipientConfirmedDelivery) {
                    const confirmButton = createElement(
                        "button",
                        "btn btn-primary",
                        "Confirm Delivery"
                    );

                    confirmButton.type = "button";

                    confirmButton.addEventListener("click", () => {
                        confirmDelivery(
                            reservation.reservationId,
                            confirmButton
                        );
                    });

                    card.appendChild(confirmButton);
                }
            }

            // Cancellation remains available for normal confirmed reservations
            if (reservation.status === "CONFIRMED") {
                const cancelButton = createElement(
                    "button",
                    "cancel-reservation-button",
                    "Cancel Reservation"
                );

                cancelButton.type = "button";

                cancelButton.addEventListener("click", () => {
                    cancelReservation(
                        reservation.reservationId,
                        cancelButton
                    );
                });

                card.appendChild(cancelButton);
            }

            reservationList.appendChild(card);
        });
    }
        // ==========================================
    // 10. RECIPIENT: LIVE DELIVERY TRACKING
    // ==========================================

    function initializeDeliveryMap() {
        const mapElement = document.getElementById("deliveryMap");

        if (!mapElement) {
            throw new Error("Delivery map element was not found.");
        }

        if (typeof L === "undefined") {
            throw new Error("Leaflet map library is not loaded.");
        }

        if (deliveryMap) {
            deliveryMap.invalidateSize();
            return;
        }

        deliveryMap = L.map("deliveryMap").setView([17.385, 78.4867], 12);

        L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
            attribution: "&copy; OpenStreetMap contributors"
        }).addTo(deliveryMap);
    }

    async function updateDeliveryTracking(reservationId) {
        try {
            const response = await fetch(
                `/api/reservations/${reservationId}/delivery-location`,
                {
                    method: "GET",
                    credentials: "same-origin",
                    headers: {
                        Accept: "application/json"
                    }
                }
            );

            const data = await readResponse(response);

            if (!response.ok) {
                throw new Error(
                    data?.message || "Unable to fetch donor location."
                );
            }

            const statusElement = document.getElementById("trackingStatus");

            if (
                data.deliveryLatitude == null ||
                data.deliveryLongitude == null
            ) {
                if (statusElement) {
                    statusElement.textContent =
                        "Waiting for donor to share GPS location...";
                }
                return;
            }

            const donorPosition = [
                Number(data.deliveryLatitude),
                Number(data.deliveryLongitude)
            ];

            if (!donorMarker) {
                donorMarker = L.marker(donorPosition)
                    .addTo(deliveryMap)
                    .bindPopup("Donor's current location");
            } else {
                donorMarker.setLatLng(donorPosition);
            }

            if (
                data.destinationLatitude != null &&
                data.destinationLongitude != null
            ) {
                const destinationPosition = [
                    Number(data.destinationLatitude),
                    Number(data.destinationLongitude)
                ];

                if (!destinationMarker) {
                    destinationMarker = L.marker(destinationPosition)
                        .addTo(deliveryMap)
                        .bindPopup("Delivery destination");
                } else {
                    destinationMarker.setLatLng(destinationPosition);
                }

                const bounds = L.latLngBounds(
                    donorPosition,
                    destinationPosition
                );

                deliveryMap.fitBounds(bounds, { padding: [40, 40] });

            } else {
                deliveryMap.setView(donorPosition, 15);
            }

            if (statusElement) {
                statusElement.textContent =
                    `Donor location updated: ${new Date().toLocaleTimeString()}`;
            }

        } catch (error) {
            console.error("Delivery tracking failed:", error);

            const statusElement = document.getElementById("trackingStatus");

            if (statusElement) {
                statusElement.textContent = error.message;
            }
        }
    }

    function startRecipientTracking(reservationId) {
        const section = document.getElementById("deliveryTrackingSection");

        if (!section) {
            showMessage(
                "session-message",
                "Delivery tracking section is missing from recipient-dashboard.html.",
                "error"
            );
            return;
        }

        section.style.display = "block";

        try {
            initializeDeliveryMap();
        } catch (error) {
            showMessage("session-message", error.message, "error");
            return;
        }

        if (trackingInterval) {
            clearInterval(trackingInterval);
        }

        // Reset markers when switching reservations
        donorMarker = null;
        destinationMarker = null;

        trackedReservationId = reservationId;

        updateDeliveryTracking(reservationId);

        trackingInterval = setInterval(() => {
            if (trackedReservationId === reservationId) {
                updateDeliveryTracking(reservationId);
            }
        }, 5000);

        section.scrollIntoView({
            behavior: "smooth",
            block: "start"
        });
    }

    function stopRecipientTracking() {
        if (trackingInterval) {
            clearInterval(trackingInterval);
            trackingInterval = null;
        }

        trackedReservationId = null;
    }

    // ==========================================
    // 11. RECIPIENT: CANCEL RESERVATION
    // ==========================================

    async function cancelReservation(reservationId, button) {
        try {
            button.disabled = true;
            button.textContent = "Cancelling...";

            if (!csrfDetails) {
                await refreshCsrfToken();
            }

            const response = await fetch(
                `/api/reservations/${reservationId}/cancel`,
                {
                    method: "PATCH",
                    credentials: "same-origin",
                    headers: {
                        Accept: "application/json",
                        ...csrfHeaders()
                    }
                }
            );

            const data = await readResponse(response);

            if (response.status === 403) {
                await refreshCsrfToken();

                throw new Error(
                    "Security token rejected. Please try again."
                );
            }

            if (!response.ok) {
                throw new Error(
                    data?.message ||
                    data?.error ||
                    `Cancellation failed (HTTP ${response.status}).`
                );
            }

            showMessage(
                "food-message",
                "Reservation cancelled successfully!",
                "success"
            );

            await refreshCsrfToken();
            await loadMyReservations();
            await loadRecipientDashboard();

        } catch (error) {
            console.error("Cancellation failed:", error);

            showMessage(
                "food-message",
                error.message || "Unable to cancel reservation.",
                "error"
            );

            button.disabled = false;
            button.textContent = "Cancel Reservation";
        }
    }

    // ==========================================
    // 12. RECIPIENT CONTROLS
    // ==========================================

    function setupRecipientControls() {
        const searchInput = document.getElementById("searchFood");
        const categorySelect = document.getElementById("filterCategory");
        const refreshButton = document.getElementById("refreshFoodButton");

        if (searchInput && searchInput.dataset.connected !== "true") {
            searchInput.dataset.connected = "true";
            searchInput.addEventListener("input", renderAvailableFood);
        }

        if (categorySelect && categorySelect.dataset.connected !== "true") {
            categorySelect.dataset.connected = "true";
            categorySelect.addEventListener("change", renderAvailableFood);
        }

        if (refreshButton && refreshButton.dataset.connected !== "true") {
            refreshButton.dataset.connected = "true";
            refreshButton.addEventListener("click", loadRecipientDashboard);
        }
    }

    // ==========================================
    // 13. DONOR: CREATE DONATION
    // ==========================================

    function setupDonationForm() {
        const form = document.getElementById("createDonationForm");

        if (!form || form.dataset.connected === "true") {
            return;
        }

        form.dataset.connected = "true";

        form.addEventListener("submit", async event => {
            event.preventDefault();

            if (!form.reportValidity()) {
                return;
            }

            const submitButton =
                form.querySelector('button[type="submit"]');

            if (submitButton) {
                submitButton.disabled = true;
                submitButton.textContent = "Saving donation...";
            }

            try {
                if (!csrfDetails) {
                    await refreshCsrfToken();
                }

                const payload = {
                    foodName:
                        document.getElementById("foodName").value.trim(),

                    foodCategory:
                        document.getElementById("foodCategory").value,

                    quantity:
                        Number(document.getElementById("quantity").value),

                    unit:
                        document.getElementById("unit").value,

                    expiryTime: toLocalDateTime(
                        document.getElementById("expiryTime").value
                    ),

                    pickupStart: toLocalDateTime(
                        document.getElementById("pickupStart").value
                    ),

                    pickupEnd: toLocalDateTime(
                        document.getElementById("pickupEnd").value
                    ),

                    pickupAddress:
                        document.getElementById("pickupAddress").value.trim(),

                    foodDescription:
                        document.getElementById("foodDescription").value.trim()
                };

                const response = await fetch(DONATIONS_URL, {
                    method: "POST",
                    credentials: "same-origin",
                    headers: {
                        "Content-Type": "application/json",
                        Accept: "application/json",
                        ...csrfHeaders()
                    },
                    body: JSON.stringify(payload)
                });

                const data = await readResponse(response);

                if (response.status === 403) {
                    await refreshCsrfToken();

                    throw new Error(
                        "The security token was rejected. Please submit again."
                    );
                }

                if (!response.ok) {
                    throw new Error(
                        data?.message ||
                        data?.error ||
                        `Donation could not be saved (HTTP ${response.status}).`
                    );
                }

                showMessage(
                    "donation-message",
                    "Donation saved successfully!",
                    "success"
                );

                form.reset();

                await refreshCsrfToken();
                await loadDonorDashboard();
                await loadDonorReservations();

            } catch (error) {
                console.error("Donation submission failed:", error);

                showMessage(
                    "donation-message",
                    error.message || "Unable to save the donation.",
                    "error"
                );

            } finally {
                if (submitButton) {
                    submitButton.disabled = false;
                    submitButton.textContent = "Submit donation";
                }
            }
        });
    }

    // ==========================================
    // 14. LOGOUT
    // ==========================================

    function setupLogout() {
        const button = document.getElementById("logoutButton");

        if (!button || button.dataset.connected === "true") {
            return;
        }

        button.dataset.connected = "true";

        button.addEventListener("click", async () => {
            button.disabled = true;
            button.textContent = "Logging out...";

            try {
                if (!csrfDetails) {
                    await refreshCsrfToken();
                }

                const response = await fetch("/api/auth/logout", {
                    method: "POST",
                    credentials: "same-origin",
                    headers: {
                        Accept: "application/json",
                        ...csrfHeaders()
                    }
                });

                if (!response.ok) {
                    throw new Error("Logout failed. Please try again.");
                }

                window.location.replace(LOGIN_URL);

            } catch (error) {
                console.error("Logout failed:", error);

                showMessage(
                    "session-message",
                    error.message || "Unable to log out. Please try again.",
                    "error"
                );

                button.disabled = false;
                button.textContent = "Log out";
            }
        });
    }

    // ==========================================
    // 15. PUBLIC FUNCTIONS
    // ==========================================

    function getCurrentUser() {
        return currentUser;
    }

    document.addEventListener("DOMContentLoaded", checkSession);

    return {
        checkSession,
        getCurrentUser,
        showMessage
    };
})();
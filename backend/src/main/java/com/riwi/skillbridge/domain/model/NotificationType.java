package com.riwi.skillbridge.domain.model;

// Solo el proveedor recibe notificaciones: el cliente ya ve el resultado en la pantalla donde actúa
public enum NotificationType {
    BOOKING_CREATED,
    BOOKING_CANCELLED,
    BOOKING_PAID,
    BOOKING_COMPLETED
}

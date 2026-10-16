package com.riwi.skillbridge.application.port.in;

/** HU-22: indicadores del dashboard del proveedor (solo sus propias mentorías, horarios y reservas). */
public interface GetProviderStatsUseCase {

    record OfferingCounts(int total, int approved, int pendingReview, int rejected,
                          /** Aprobadas que los clientes no ven porque no tienen horarios libres */
                          int approvedWithoutFreeSlots) {}

    record SlotCounts(int upcomingFree, int upcomingReserved) {}

    record BookingCounts(int total, int upcoming, int pendingPayment, int confirmed, int completed, int cancelled) {}

    record ProviderStats(OfferingCounts offerings, SlotCounts slots, BookingCounts bookings) {}

    ProviderStats statsFor(String providerEmail);
}

export interface BookingEvent {
    bookingId: string;
    offeringId: string;
    customerId: string;
    scheduledAt: string;
    occurredAt: string;
}
export declare const BOOKING_ROUTING_KEYS: {
    readonly CREATED: "booking.created";
    readonly PAID: "booking.paid";
    readonly CANCELLED: "booking.cancelled";
    readonly COMPLETED: "booking.completed";
};
export type BookingRoutingKey = typeof BOOKING_ROUTING_KEYS[keyof typeof BOOKING_ROUTING_KEYS];

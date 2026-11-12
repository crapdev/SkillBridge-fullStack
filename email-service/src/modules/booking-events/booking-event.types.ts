// Mirrors the current Java BookingEvent. Change only after agreement with Spring Boot.
export interface BookingEvent {
  bookingId: string;
  offeringId: string;
  customerId: string;
  scheduledAt: string;
  occurredAt: string;
}

export const BOOKING_ROUTING_KEYS = {
  CREATED: 'booking.created',
  PAID: 'booking.paid',
  CANCELLED: 'booking.cancelled',
  COMPLETED: 'booking.completed'
} as const;

export type BookingRoutingKey = typeof BOOKING_ROUTING_KEYS[keyof typeof BOOKING_ROUTING_KEYS];

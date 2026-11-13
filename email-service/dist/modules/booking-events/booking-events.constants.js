"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.BOOKING_EMAIL_ROUTING_KEYS = exports.BOOKING_EMAILS_DLQ = exports.BOOKING_EMAILS_DLX = exports.BOOKING_EMAILS_QUEUE = exports.BOOKING_EVENTS_EXCHANGE_TYPE = exports.BOOKING_EVENTS_EXCHANGE = void 0;
exports.BOOKING_EVENTS_EXCHANGE = 'booking.events';
exports.BOOKING_EVENTS_EXCHANGE_TYPE = 'topic';
exports.BOOKING_EMAILS_QUEUE = 'booking.emails.queue';
exports.BOOKING_EMAILS_DLX = 'booking.emails.dlx';
exports.BOOKING_EMAILS_DLQ = 'booking.emails.dlq';
exports.BOOKING_EMAIL_ROUTING_KEYS = [
    'booking.created',
    'booking.paid',
    'booking.cancelled',
    'booking.completed'
];
//# sourceMappingURL=booking-events.constants.js.map
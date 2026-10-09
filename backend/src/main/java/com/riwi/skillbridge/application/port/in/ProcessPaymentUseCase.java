package com.riwi.skillbridge.application.port.in;

public interface ProcessPaymentUseCase {
    void confirmPaymentSuccess(String stripeIntentId);
    void markPaymentFailed(String stripeIntentId);
}

package com.riwi.skillbridge.infrastructure.adapter.out.payment;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StripePaymentGatewayAdapterTest {

    @Test
    void copIsSentInCents() {
        // Antes se enviaba 85000 y Stripe lo interpretaba como $850,00 COP (amount_too_small)
        assertEquals(8_500_000L, StripePaymentGatewayAdapter.toMinorUnits(new BigDecimal("85000.00"), "COP"));
    }

    @Test
    void currencyCodeIsCaseInsensitive() {
        assertEquals(12_000_000L, StripePaymentGatewayAdapter.toMinorUnits(new BigDecimal("120000"), "cop"));
    }
}

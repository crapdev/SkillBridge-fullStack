package com.riwi.skillbridge.infrastructure.adapter.out.payment;

import com.riwi.skillbridge.application.port.out.PaymentGatewayPort;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.stripe.exception.StripeException;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Locale;
import java.util.UUID;

@Component
public class StripePaymentGatewayAdapter implements PaymentGatewayPort {

    private static final Logger log = LoggerFactory.getLogger(StripePaymentGatewayAdapter.class);

    private final RequestOptions requestOptions;

    // La clave se pasa en cada llamada en lugar de usar el estático global Stripe.apiKey
    public StripePaymentGatewayAdapter(@Value("${stripe.api.key.secret}") String secretKey) {
        this.requestOptions = RequestOptions.builder().setApiKey(secretKey).build();
    }

    @Override
    public PaymentIntent createPaymentIntent(UUID bookingId, BigDecimal amount, String currency) {
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
            .setAmount(toMinorUnits(amount, currency))
            .setCurrency(currency.toLowerCase(Locale.ROOT))
            .putMetadata("bookingId", bookingId.toString())
            .build();

        try {
            com.stripe.model.PaymentIntent intent = com.stripe.model.PaymentIntent.create(params, requestOptions);
            return new PaymentIntent(intent.getId(), intent.getClientSecret());
        } catch (StripeException ex) {
            log.warn("Stripe rechazó el pago de la reserva {}: [{}] {}", bookingId, ex.getCode(), ex.getMessage());
            throw new BusinessRuleException("No fue posible iniciar el pago; inténtalo de nuevo");
        }
    }

    @Override
    public PaymentOutcome checkOutcome(String paymentIntentId) {
        try {
            com.stripe.model.PaymentIntent intent = com.stripe.model.PaymentIntent.retrieve(paymentIntentId, requestOptions);
            return switch (intent.getStatus()) {
                case "succeeded" -> PaymentOutcome.SUCCEEDED;
                case "canceled" -> PaymentOutcome.FAILED;
                // Tras un intento rechazado Stripe vuelve a pedir un método de pago y guarda el error del intento
                case "requires_payment_method" -> intent.getLastPaymentError() != null ? PaymentOutcome.FAILED : PaymentOutcome.PENDING;
                default -> PaymentOutcome.PENDING;
            };
        } catch (StripeException ex) {
            log.warn("No se pudo consultar el pago {} en Stripe: [{}] {}", paymentIntentId, ex.getCode(), ex.getMessage());
            throw new BusinessRuleException("No fue posible verificar el pago; inténtalo de nuevo");
        }
    }

    /**
     * Stripe espera el monto como entero en la unidad mínima de la moneda: 85.000 COP se envía
     * como 8500000 (centavos). Enviar 85000 cobraría $850,00 COP.
     */
    static long toMinorUnits(BigDecimal amount, String currency) {
        int decimals = Currency.getInstance(currency.toUpperCase(Locale.ROOT)).getDefaultFractionDigits();
        return amount.movePointRight(decimals).longValueExact();
    }
}

package com.riwi.skillbridge.infrastructure.adapter.out.payment;

import com.riwi.skillbridge.application.port.out.PaymentGatewayPort;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.stripe.exception.StripeException;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

@Component
public class StripePaymentGatewayAdapter implements PaymentGatewayPort {

    private final RequestOptions requestOptions;

    // La clave se pasa en cada llamada en lugar de usar el estático global Stripe.apiKey
    public StripePaymentGatewayAdapter(@Value("${stripe.api.key.secret}") String secretKey) {
        this.requestOptions = RequestOptions.builder().setApiKey(secretKey).build();
    }

    @Override
    public PaymentIntent createPaymentIntent(UUID bookingId, BigDecimal amount, String currency) {
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
            // Stripe recibe el monto como entero en la unidad mínima de la moneda
            .setAmount(amount.longValue())
            .setCurrency(currency.toLowerCase(Locale.ROOT))
            .putMetadata("bookingId", bookingId.toString())
            .build();

        try {
            com.stripe.model.PaymentIntent intent = com.stripe.model.PaymentIntent.create(params, requestOptions);
            return new PaymentIntent(intent.getId(), intent.getClientSecret());
        } catch (StripeException ex) {
            throw new BusinessRuleException("No fue posible iniciar el pago; inténtalo de nuevo");
        }
    }
}

package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.CreatePaymentUseCase;
import com.riwi.skillbridge.application.port.in.ProcessPaymentUseCase;
import com.riwi.skillbridge.application.port.in.SyncPaymentUseCase;
import com.riwi.skillbridge.domain.model.PaymentStatus;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.CreatePaymentRequest;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.PaymentSecretResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.net.Webhook;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final ProcessPaymentUseCase processPaymentUseCase;
    private final CreatePaymentUseCase createPaymentUseCase;
    private final SyncPaymentUseCase syncPaymentUseCase;

    // Se inyecta desde la variable de entorno STRIPE_WEBHOOK_SECRET (ej. whsec_.....)
    @Value("${stripe.webhook.secret}")
    private String endpointSecret;

    public PaymentController(ProcessPaymentUseCase processPaymentUseCase, CreatePaymentUseCase createPaymentUseCase,
                             SyncPaymentUseCase syncPaymentUseCase) {
        this.processPaymentUseCase = processPaymentUseCase;
        this.createPaymentUseCase = createPaymentUseCase;
        this.syncPaymentUseCase = syncPaymentUseCase;
    }

    // Al terminar el checkout, el frontend pide verificar el pago; el backend lo consulta directamente en Stripe
    @PostMapping("/{paymentIntentId}/sync")
    public Map<String, PaymentStatus> syncPayment(@PathVariable String paymentIntentId, Authentication authentication) {
        return Map.of("status", syncPaymentUseCase.syncPayment(paymentIntentId, authentication.getName()));
    }

    // El cliente inicia el pago de una reserva propia; el frontend usa el secreto con Stripe Elements
    @PostMapping("/intent")
    public PaymentSecretResponse createPaymentIntent(@Valid @RequestBody CreatePaymentRequest request,
                                                     Authentication authentication) {
        return new PaymentSecretResponse(
            createPaymentUseCase.createPaymentIntent(request.bookingId(), authentication.getName()));
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleStripeWebhook(
        @RequestBody String payload,
        @RequestHeader("Stripe-Signature") String sigHeader) {

        Event event;

        try {
            // 1. Verificación de Seguridad: Comprueba que el evento sea auténtico
            event = Webhook.constructEvent(payload, sigHeader, endpointSecret);
        } catch (SignatureVerificationException e) {
            System.err.println("Firma de Stripe inválida.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Firma inválida");
        } catch (Exception e) {
            System.err.println("Error procesando payload de Stripe.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Error procesando payload");
        }

        // 2. Extraer el objeto PaymentIntent
        PaymentIntent paymentIntent = null;
        if (event.getDataObjectDeserializer().getObject().isPresent()) {
            Object stripeObject = event.getDataObjectDeserializer().getObject().get();
            if (stripeObject instanceof PaymentIntent) {
                paymentIntent = (PaymentIntent) stripeObject;
            }
        }

        if (paymentIntent == null) {
            return ResponseEntity.ok("Evento ignorado (no es un PaymentIntent)");
        }

        // 3. Evaluar el estado y llamar a nuestro Caso de Uso
        switch (event.getType()) {
            case "payment_intent.succeeded":
                System.out.println("Pago exitoso. Intent ID: " + paymentIntent.getId());
                processPaymentUseCase.confirmPaymentSuccess(paymentIntent.getId());
                break;
            case "payment_intent.payment_failed":
                System.out.println("Pago fallido. Intent ID: " + paymentIntent.getId());
                processPaymentUseCase.markPaymentFailed(paymentIntent.getId());
                break;
            default:
                System.out.println("Evento no manejado: " + event.getType());
                break;
        }

        // 4. Importante: Stripe exige que se le responda con 200 OK rápidamente
        return ResponseEntity.ok("Success");
    }
}

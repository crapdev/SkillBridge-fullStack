package com.riwi.skillbridge.infrastructure.adapter.in.scheduling;

import com.riwi.skillbridge.application.port.in.CompleteFinishedBookingsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * BUG-05: cada cierto tiempo pasa a COMPLETED las reservas confirmadas cuya sesión ya terminó.
 * En Render gratis el servidor se duerme; por eso las consultas de reservas también lo revisan.
 */
@Component
@ConditionalOnProperty(name = "app.bookings.completion.enabled", havingValue = "true", matchIfMissing = true)
public class BookingCompletionScheduler {
    private static final Logger log = LoggerFactory.getLogger(BookingCompletionScheduler.class);

    private final CompleteFinishedBookingsUseCase useCase;

    public BookingCompletionScheduler(CompleteFinishedBookingsUseCase useCase) {
        this.useCase = useCase;
    }

    @Scheduled(fixedDelayString = "${app.bookings.completion.interval-ms:300000}",
               initialDelayString = "${app.bookings.completion.initial-delay-ms:60000}")
    public void completeFinishedBookings() {
        try {
            int completed = useCase.completeFinished();
            if (completed > 0) {
                log.info("Reservas completadas: {}", completed);
            }
        } catch (RuntimeException e) {
            // Un fallo (base caída, Rabbit caído) no detiene el proceso: se intenta de nuevo en la próxima vuelta
            log.error("No se pudieron completar las reservas terminadas", e);
        }
    }
}

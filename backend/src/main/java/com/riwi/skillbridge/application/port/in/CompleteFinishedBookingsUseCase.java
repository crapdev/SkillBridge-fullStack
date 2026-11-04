package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.domain.model.Booking;

import java.util.List;

/** BUG-05: pasa a COMPLETED las reservas confirmadas cuya sesión ya terminó. */
public interface CompleteFinishedBookingsUseCase {
    /** Revisa todas las reservas confirmadas (lo usa el proceso programado). Devuelve cuántas completó. */
    int completeFinished();

    /**
     * Revisa solo las reservas que se van a mostrar y las devuelve con su estado al día. El servidor gratuito
     * se duerme y el proceso programado puede no haber corrido: quien consulta no ve una sesión vieja como pendiente.
     */
    List<Booking> completeIfFinished(List<Booking> bookings);
}

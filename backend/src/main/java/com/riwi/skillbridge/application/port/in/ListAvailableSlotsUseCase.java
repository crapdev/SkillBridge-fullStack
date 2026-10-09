package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.domain.model.AvailabilitySlot;

import java.util.List;
import java.util.UUID;

public interface ListAvailableSlotsUseCase {
    /** Horarios futuros y libres de una mentoría: los que el cliente puede reservar. */
    List<AvailabilitySlot> listAvailableSlots(UUID offeringId);
}

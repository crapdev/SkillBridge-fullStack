package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.ListAvailableSlotsUseCase;
import com.riwi.skillbridge.application.port.in.OfferingDraft;
import com.riwi.skillbridge.application.port.out.AvailabilitySlotRepositoryPort;
import com.riwi.skillbridge.application.port.out.OfferingCachePort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.exception.UnauthorizedActionException;
import com.riwi.skillbridge.domain.model.AvailabilitySlot;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.OfferingReviewStatus;
import com.riwi.skillbridge.domain.model.SyllabusTopic;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ProviderOfferingService implements ListAvailableSlotsUseCase {

    /** Los proveedores publican horarios en la hora de Colombia: con ella se decide el día de la semana. */
    static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Bogota");
    // 24 h: "a. m." usa espacios invisibles (U+202F) que se ven mal en los mensajes de error
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm").withZone(BUSINESS_ZONE);
    private static final Locale SPANISH = Locale.forLanguageTag("es-CO");

    private final OfferingRepositoryPort offeringRepositoryPort;
    private final AvailabilitySlotRepositoryPort slotRepositoryPort;
    private final UserAccountPort userAccountPort;
    private final OfferingCachePort offeringCache;

    public ProviderOfferingService(
        OfferingRepositoryPort offeringRepositoryPort,
        AvailabilitySlotRepositoryPort slotRepositoryPort,
        UserAccountPort userAccountPort,
        OfferingCachePort offeringCache
    ) {
        this.offeringRepositoryPort = offeringRepositoryPort;
        this.slotRepositoryPort = slotRepositoryPort;
        this.userAccountPort = userAccountPort;
        this.offeringCache = offeringCache;
    }

    // CREATE OFFERING (HU-20: CON SU FICHA COMPLETA)
    public Offering createOffering(OfferingDraft draft, String providerEmail) {
        UUID providerId = getProviderId(providerEmail);
        validateDraft(draft);
        // HU-21: toda mentoría nueva queda en revisión y no es visible hasta que un administrador la apruebe
        Offering offering = toOffering(UUID.randomUUID(), providerId, true, draft,
            OfferingReviewStatus.PENDING_REVIEW, null);
        Offering saved = offeringRepositoryPort.save(offering);
        // El catálogo público está en caché: sin invalidarlo, la mentoría nueva no aparece hasta que expire
        offeringCache.evictActiveOfferings();
        return saved;
    }

    // NUEVO MÉTODO: Trae la lista de mentorías de este proveedor específico
    public List<Offering> getOwnedOfferings(String providerEmail) {
        // 1. Buscamos el ID del proveedor usando su correo
        UUID providerId = getProviderId(providerEmail);

        // 2. Buscamos todas las mentorías asociadas a ese ID
        return offeringRepositoryPort.findByProviderId(providerId);
    }

    // UPDATE OFFERING (HU-19/HU-20): SOLO EL DUEÑO PUEDE EDITAR LOS DATOS Y LA FICHA DE LA MENTORÍA
    public Offering updateOffering(UUID offeringId, OfferingDraft draft, String providerEmail) {
        Offering existing = getOwnedOffering(offeringId, providerEmail);
        validateDraft(draft);

        // Dueño, id y estado se conservan: el proveedor no puede cambiarlos desde el formulario.
        // HU-21: corregir una mentoría rechazada la reenvía a revisión; una aprobada sigue aprobada
        Offering review = existing.resubmitted();
        Offering changes = toOffering(existing.id(), existing.providerId(), existing.active(), draft,
            review.reviewStatus(), review.rejectionReason());
        Offering updated = offeringRepositoryPort.update(changes);
        // El catálogo público está en caché: sin invalidarlo seguiría mostrando los datos viejos
        offeringCache.evictActiveOfferings();
        return updated;
    }

    // DELETE OFFERING (VALIDATE THE OWNER IS PROVIDER)
    public void deleteOffering(UUID offeringId, String providerEmail) {
        Offering existing = getOwnedOffering(offeringId, providerEmail);
        // Los horarios dependen de la mentoría: sin borrarlos antes, la base de datos rechaza la eliminación
        if (slotRepositoryPort.findByOfferingId(existing.id()).stream().anyMatch(AvailabilitySlot::reserved)) {
            throw new BusinessRuleException("No puedes eliminar una mentoría que tiene horarios reservados");
        }
        slotRepositoryPort.deleteByOfferingId(existing.id());
        offeringRepositoryPort.deleteById(existing.id());
        offeringCache.evictActiveOfferings();
    }

    // CREATE AVAILABILITY (ASSIGN DATE AND HOURS SPECIFIC TO THE OFFERING)
    public AvailabilitySlot addAvailabilitySlot(UUID offeringId, Instant scheduledAt, String providerEmail) {
        if (scheduledAt.isBefore(Instant.now())) {
            throw new BusinessRuleException("El horario disponible debe ser en una fecha futura");
        }
        Offering offering = getOwnedOffering(offeringId, providerEmail);

        // HU-20: el horario debe caer en uno de los días que el proveedor ofrece (en la hora de Colombia)
        DayOfWeek day = scheduledAt.atZone(BUSINESS_ZONE).getDayOfWeek();
        if (!offering.offersOn(day)) {
            throw new BusinessRuleException("Esta mentoría solo se ofrece los días " + daysText(offering.workingDays())
                + ". El " + dayName(day) + " no está entre ellos");
        }

        if (slotRepositoryPort.existsByOfferingIdAndScheduledAt(offeringId, scheduledAt)) {
            throw new BusinessRuleException("Ya registraste este horario para esta mentoría");
        }
        rejectOverlaps(offering, scheduledAt);

        AvailabilitySlot slot = new AvailabilitySlot(UUID.randomUUID(), offeringId, scheduledAt, false);
        AvailabilitySlot saved = slotRepositoryPort.save(slot);
        offeringCache.evictActiveOfferings();
        return saved;
    }

    /** Horario que no se publicó en un lote y por qué */
    public record SkippedSlot(Instant scheduledAt, String reason) {}

    /** Resultado de publicar varios horarios: los creados y los omitidos */
    public record SlotBatchResult(List<AvailabilitySlot> created, List<SkippedSlot> skipped) {}

    /** Un horario ya ocupado del proveedor: de su inicio a su fin y de qué mentoría es */
    private record BusySlot(Offering offering, Instant start, Instant end) {}

    // PUBLISH SEVERAL SLOTS AT ONCE (THE SAME HOURS REPEATED ON THE DAYS THE OFFERING IS GIVEN)
    /**
     * Mismas reglas que {@link #addAvailabilitySlot}, pero un horario que no se puede publicar (pasado, día no
     * ofrecido, ya publicado o cruzado con otra sesión) se omite con su motivo en vez de cancelar todo el lote.
     * Los horarios ocupados se cargan una sola vez y los nuevos se suman, así también se detectan cruces entre ellos.
     */
    public SlotBatchResult addAvailabilitySlots(UUID offeringId, List<Instant> starts, String providerEmail) {
        Offering offering = getOwnedOffering(offeringId, providerEmail);
        Duration duration = Duration.ofMinutes(offering.durationMinutes());
        List<BusySlot> busy = busySlots(offering.providerId());
        Instant now = Instant.now();

        List<AvailabilitySlot> created = new ArrayList<>();
        List<SkippedSlot> skipped = new ArrayList<>();
        for (Instant start : starts.stream().distinct().sorted().toList()) {
            String reason = skipReason(offering, start, now, busy);
            if (reason != null) {
                skipped.add(new SkippedSlot(start, reason));
                continue;
            }
            created.add(slotRepositoryPort.save(new AvailabilitySlot(UUID.randomUUID(), offeringId, start, false)));
            busy.add(new BusySlot(offering, start, start.plus(duration)));
        }
        if (!created.isEmpty()) {
            offeringCache.evictActiveOfferings();
        }
        return new SlotBatchResult(created, skipped);
    }

    // SHOW AVAILABLE SLOTS TO CUSTOMERS (ONLY NOT RESERVED AND IN THE FUTURE)
    @Override
    public List<AvailabilitySlot> listAvailableSlots(UUID offeringId) {
        Instant now = Instant.now();
        return slotRepositoryPort.findAvailableByOfferingId(offeringId).stream()
            .filter(slot -> slot.scheduledAt().isAfter(now))
            .toList();
    }

    // LIST THE PROVIDER'S UPCOMING SLOTS (FREE AND RESERVED) FOR ONE OF THEIR OFFERINGS
    public List<AvailabilitySlot> listOwnedSlots(UUID offeringId, String providerEmail) {
        getOwnedOffering(offeringId, providerEmail);
        Instant now = Instant.now();
        return slotRepositoryPort.findByOfferingId(offeringId).stream()
            .filter(slot -> slot.scheduledAt().isAfter(now))
            .toList();
    }

    // DELETE A FREE SLOT (A RESERVED ONE ALREADY HAS A CUSTOMER BOOKING)
    public void deleteAvailabilitySlot(UUID offeringId, UUID slotId, String providerEmail) {
        getOwnedOffering(offeringId, providerEmail);
        AvailabilitySlot slot = slotRepositoryPort.findById(slotId)
            .filter(s -> s.offeringId().equals(offeringId))
            .orElseThrow(() -> new DomainNotFoundException("Horario no encontrado"));
        if (slot.reserved()) {
            throw new BusinessRuleException("No puedes eliminar un horario que ya fue reservado");
        }
        slotRepositoryPort.deleteById(slotId);
        offeringCache.evictActiveOfferings();
    }

    public Offering getOwnedOffering(UUID offeringId, String providerEmail) {
        UUID providerId = getProviderId(providerEmail);
        Offering offering = offeringRepositoryPort.findById(offeringId)
            .orElseThrow(() -> new DomainNotFoundException("Mentoría no encontrada"));

        if (!offering.providerId().equals(providerId)) {
            // 403: la mentoría existe pero pertenece a otro proveedor
            throw new UnauthorizedActionException("No tienes permisos para modificar una mentoría de otro proveedor");
        }
        return offering;
    }

    // Segunda barrera además de Bean Validation: el servicio no confía en que el llamador ya validó
    private void validateDraft(OfferingDraft draft) {
        if (draft == null || isBlank(draft.title()) || isBlank(draft.description()) || isBlank(draft.category())) {
            throw new BusinessRuleException("Título, descripción y categoría son obligatorios");
        }
        if (draft.price() == null || draft.price().signum() <= 0) {
            throw new BusinessRuleException("El precio debe ser mayor a cero");
        }
        List<SyllabusTopic> syllabus = draft.syllabus();
        if (syllabus == null || syllabus.size() < OfferingDraft.MIN_TOPICS) {
            throw new BusinessRuleException("El temario debe tener al menos 2 temas");
        }
        if (syllabus.size() > OfferingDraft.MAX_TOPICS) {
            throw new BusinessRuleException("El temario admite máximo 12 temas");
        }
        if (syllabus.stream().anyMatch(t -> t == null || isBlank(t.title()) || isBlank(t.description()))) {
            throw new BusinessRuleException("Cada tema necesita título y descripción");
        }
        Integer duration = draft.durationMinutes();
        if (duration == null || duration < OfferingDraft.MIN_DURATION_MINUTES || duration > OfferingDraft.MAX_DURATION_MINUTES) {
            throw new BusinessRuleException("La duración debe estar entre 30 y 240 minutos");
        }
        if (draft.level() == null) {
            throw new BusinessRuleException("El nivel es obligatorio");
        }
        if (draft.workingDays() == null || draft.workingDays().isEmpty()) {
            throw new BusinessRuleException("Elige al menos un día de la semana");
        }
    }

    /**
     * Un mentor no puede dar dos sesiones a la vez: cada horario ocupa de su inicio a su fin (inicio + duración)
     * y el nuevo no puede cruzarse con ningún otro del mismo proveedor, sea de esta mentoría o de otra suya.
     * Los intervalos son [inicio, fin): una sesión puede empezar justo cuando termina la anterior.
     */
    private void rejectOverlaps(Offering offering, Instant start) {
        Instant end = start.plus(Duration.ofMinutes(offering.durationMinutes()));
        for (Offering own : offeringRepositoryPort.findByProviderId(offering.providerId())) {
            Duration ownDuration = Duration.ofMinutes(own.durationMinutes());
            for (AvailabilitySlot other : slotRepositoryPort.findByOfferingId(own.id())) {
                Instant otherEnd = other.scheduledAt().plus(ownDuration);
                if (start.isBefore(otherEnd) && other.scheduledAt().isBefore(end)) {
                    String which = own.id().equals(offering.id())
                        ? "otra sesión de esta mentoría"
                        : "una sesión de «" + own.title() + "»";
                    throw new BusinessRuleException("Este horario se cruza con " + which + " de "
                        + HOUR.format(other.scheduledAt()) + " a " + HOUR.format(otherEnd)
                        + ". Cada sesión de esta mentoría dura " + offering.durationMinutes() + " minutos");
                }
            }
        }
    }

    /** Por qué no se puede publicar un horario del lote (null = se puede) */
    private static String skipReason(Offering offering, Instant start, Instant now, List<BusySlot> busy) {
        if (!start.isAfter(now)) {
            return "La fecha ya pasó";
        }
        DayOfWeek day = start.atZone(BUSINESS_ZONE).getDayOfWeek();
        if (!offering.offersOn(day)) {
            return "La mentoría no se ofrece los " + dayName(day);
        }
        Instant end = start.plus(Duration.ofMinutes(offering.durationMinutes()));
        for (BusySlot other : busy) {
            if (other.offering().id().equals(offering.id()) && other.start().equals(start)) {
                return "Ya estaba publicado";
            }
            if (start.isBefore(other.end()) && other.start().isBefore(end)) {
                String which = other.offering().id().equals(offering.id())
                    ? "otra sesión de esta mentoría"
                    : "una sesión de «" + other.offering().title() + "»";
                return "Se cruza con " + which + " de " + HOUR.format(other.start()) + " a " + HOUR.format(other.end());
            }
        }
        return null;
    }

    /** Todos los horarios del proveedor, de cualquiera de sus mentorías, con el intervalo que ocupan */
    private List<BusySlot> busySlots(UUID providerId) {
        List<BusySlot> busy = new ArrayList<>();
        for (Offering own : offeringRepositoryPort.findByProviderId(providerId)) {
            Duration ownDuration = Duration.ofMinutes(own.durationMinutes());
            for (AvailabilitySlot slot : slotRepositoryPort.findByOfferingId(own.id())) {
                busy.add(new BusySlot(own, slot.scheduledAt(), slot.scheduledAt().plus(ownDuration)));
            }
        }
        return busy;
    }

    // Textos sin espacios sobrantes al inicio y al final
    private static Offering toOffering(UUID id, UUID providerId, boolean active, OfferingDraft draft,
                                       OfferingReviewStatus reviewStatus, String rejectionReason) {
        return new Offering(
            id,
            providerId,
            draft.title().trim(),
            draft.description().trim(),
            draft.category().trim(),
            draft.price(),
            active,
            draft.syllabus().stream()
                .map(t -> new SyllabusTopic(t.title().trim(), t.description().trim()))
                .toList(),
            draft.durationMinutes(),
            draft.level(),
            draft.workingDays(),
            reviewStatus,
            rejectionReason
        );
    }

    // "lunes, miércoles y viernes"
    private static String daysText(Set<DayOfWeek> days) {
        List<String> names = days.stream().map(ProviderOfferingService::dayName).toList();
        if (names.size() == 1) return names.get(0);
        return String.join(", ", names.subList(0, names.size() - 1)) + " y " + names.get(names.size() - 1);
    }

    private static String dayName(DayOfWeek day) {
        return day.getDisplayName(TextStyle.FULL, SPANISH);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private UUID getProviderId(String email) {
        return userAccountPort.findIdByEmail(email)
            .orElseThrow(() -> new DomainNotFoundException("Proveedor no encontrado"));
    }
}

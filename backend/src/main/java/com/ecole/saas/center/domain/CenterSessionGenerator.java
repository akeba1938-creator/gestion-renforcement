package com.ecole.saas.center.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Génération pure et déterministe des occurrences de séances datées à partir des créneaux récurrents.
 */
public final class CenterSessionGenerator {

    public static final int MAX_GENERATION_DAYS = 93;
    public static final int MAX_GENERATED_SESSIONS = 200;

    private CenterSessionGenerator() {
    }

    public record PlannedOccurrence(
            UUID scheduleSlotId,
            UUID teacherId,
            UUID subjectId,
            LocalDate sessionDate,
            LocalTime startTime,
            LocalTime endTime,
            String roomName) {
    }

    public static List<PlannedOccurrence> generateOccurrences(
            CenterGroup group,
            List<CenterScheduleSlot> activeSlots,
            LocalDate fromDate,
            LocalDate toDate) {
        if (group == null) {
            throw new CenterValidationException("center-generation-invalid", "Cohorte invalide", "La cohorte est obligatoire.");
        }
        if (fromDate == null || toDate == null || toDate.isBefore(fromDate)) {
            throw new CenterValidationException(
                    "center-generation-invalid",
                    "Plage de dates invalide",
                    "La date de fin de génération doit être postérieure ou égale à la date de début.");
        }
        long spanDays = ChronoUnit.DAYS.between(fromDate, toDate) + 1;
        if (spanDays > MAX_GENERATION_DAYS) {
            throw new CenterValidationException(
                    "center-generation-invalid",
                    "Plage de génération trop large",
                    "La génération de séances est limitée à " + MAX_GENERATION_DAYS + " jours par opération.");
        }
        List<CenterScheduleSlot> sortedSlots = activeSlots.stream()
                .filter(CenterScheduleSlot::isActive)
                .sorted(Comparator.comparing(CenterScheduleSlot::getDayOfWeek)
                        .thenComparing(CenterScheduleSlot::getStartTime))
                .toList();
        if (sortedSlots.isEmpty()) {
            return List.of();
        }

        List<PlannedOccurrence> occurrences = new ArrayList<>();
        for (LocalDate current = fromDate; !current.isAfter(toDate); current = current.plusDays(1)) {
            if (current.isBefore(group.getStartDate())) {
                continue;
            }
            if (group.getEndDate() != null && current.isAfter(group.getEndDate())) {
                continue;
            }
            for (CenterScheduleSlot slot : sortedSlots) {
                if (slot.appliesOnDate(current)) {
                    occurrences.add(new PlannedOccurrence(
                            slot.getId(),
                            slot.getTeacherId(),
                            slot.getSubjectId() != null ? slot.getSubjectId() : group.getSubjectId(),
                            current,
                            slot.getStartTime(),
                            slot.getEndTime(),
                            slot.getRoomName() != null ? slot.getRoomName() : group.getRoomName()));
                    if (occurrences.size() > MAX_GENERATED_SESSIONS) {
                        throw new CenterValidationException(
                                "center-generation-invalid",
                                "Trop de séances à générer",
                                "Le nombre maximal de séances générées par lot (" + MAX_GENERATED_SESSIONS + ") est dépassé.");
                    }
                }
            }
        }
        return occurrences;
    }
}

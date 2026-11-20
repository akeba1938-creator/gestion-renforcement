package com.ecole.saas.center.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

/**
 * Détection pure des chevauchements horaires sur créneaux hebdomadaires et séances datées.
 */
public final class CenterScheduleConflictChecker {

    private CenterScheduleConflictChecker() {
    }

    public static boolean timesOverlap(LocalTime startA, LocalTime endA, LocalTime startB, LocalTime endB) {
        Objects.requireNonNull(startA, "startA obligatoire");
        Objects.requireNonNull(endA, "endA obligatoire");
        Objects.requireNonNull(startB, "startB obligatoire");
        Objects.requireNonNull(endB, "endB obligatoire");
        return startA.isBefore(endB) && startB.isBefore(endA);
    }

    public static boolean dateRangesOverlap(
            LocalDate fromA, LocalDate untilA, LocalDate fromB, LocalDate untilB) {
        if (untilA != null && fromB != null && untilA.isBefore(fromB)) {
            return false;
        }
        return untilB == null || fromA == null || !untilB.isBefore(fromA);
    }

    public static boolean slotsConflict(
            DayOfWeek dayA,
            LocalTime startA,
            LocalTime endA,
            LocalDate fromA,
            LocalDate untilA,
            CenterScheduleSlot existing) {
        if (existing == null || !existing.isActive()) {
            return false;
        }
        if (dayA != existing.getDayOfWeek()) {
            return false;
        }
        if (!timesOverlap(startA, endA, existing.getStartTime(), existing.getEndTime())) {
            return false;
        }
        return dateRangesOverlap(fromA, untilA, existing.getEffectiveFrom(), existing.getEffectiveUntil());
    }

    public static void assertNoSlotConflict(
            UUID excludedSlotId,
            DayOfWeek dayOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            LocalDate effectiveFrom,
            LocalDate effectiveUntil,
            Collection<CenterScheduleSlot> existingSlots,
            String conflictMessage) {
        for (CenterScheduleSlot slot : existingSlots) {
            if (excludedSlotId != null && excludedSlotId.equals(slot.getId())) {
                continue;
            }
            if (slotsConflict(dayOfWeek, startTime, endTime, effectiveFrom, effectiveUntil, slot)) {
                throw new CenterConflictException(
                        "center-schedule-conflict",
                        "Conflit de créneau horaire",
                        conflictMessage);
            }
        }
    }

    public static void assertNoSessionConflict(
            UUID excludedSessionId,
            LocalDate sessionDate,
            LocalTime startTime,
            LocalTime endTime,
            Collection<CenterSession> existingSessions,
            String conflictMessage) {
        for (CenterSession session : existingSessions) {
            if (session.getStatus() == CenterSessionStatus.CANCELLED) {
                continue;
            }
            if (excludedSessionId != null && excludedSessionId.equals(session.getId())) {
                continue;
            }
            if (sessionDate.equals(session.getSessionDate())
                    && timesOverlap(startTime, endTime, session.getStartTime(), session.getEndTime())) {
                throw new CenterConflictException(
                        "center-schedule-conflict",
                        "Conflit d'horaire de séance",
                        conflictMessage);
            }
        }
    }
}

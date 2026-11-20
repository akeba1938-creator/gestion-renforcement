package com.ecole.saas.center.domain;

import com.ecole.saas.shared.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Créneau récurrent hebdomadaire d'une cohorte de renforcement.
 */
@Entity
@Table(name = "center_schedule_slots")
public class CenterScheduleSlot extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "group_id", nullable = false, updatable = false)
    private UUID groupId;

    @Column(name = "teacher_id")
    private UUID teacherId;

    @Column(name = "subject_id")
    private UUID subjectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 15)
    private DayOfWeek dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "room_name", length = 60)
    private String roomName;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_until")
    private LocalDate effectiveUntil;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected CenterScheduleSlot() {
    }

    public CenterScheduleSlot(
            UUID groupId,
            UUID teacherId,
            UUID subjectId,
            DayOfWeek dayOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            String roomName,
            LocalDate effectiveFrom,
            LocalDate effectiveUntil) {
        this.groupId = Objects.requireNonNull(groupId, "La cohorte est obligatoire.");
        this.teacherId = teacherId;
        this.subjectId = subjectId;
        this.dayOfWeek = requireDayOfWeek(dayOfWeek);
        validateTimes(startTime, endTime);
        validateDates(effectiveFrom, effectiveUntil);
        this.startTime = startTime;
        this.endTime = endTime;
        this.roomName = normalizeOptional(roomName, 60);
        this.effectiveFrom = effectiveFrom;
        this.effectiveUntil = effectiveUntil;
        this.active = true;
    }

    public void update(
            UUID teacherId,
            UUID subjectId,
            DayOfWeek dayOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            String roomName,
            LocalDate effectiveFrom,
            LocalDate effectiveUntil) {
        if (!this.active) {
            throw new CenterConflictException(
                    "center-slot-inactive",
                    "Créneau désactivé",
                    "Un créneau désactivé ne peut plus être modifié.");
        }
        this.dayOfWeek = requireDayOfWeek(dayOfWeek);
        validateTimes(startTime, endTime);
        validateDates(effectiveFrom, effectiveUntil);
        this.teacherId = teacherId;
        this.subjectId = subjectId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.roomName = normalizeOptional(roomName, 60);
        this.effectiveFrom = effectiveFrom;
        this.effectiveUntil = effectiveUntil;
    }

    public void deactivate() {
        if (!this.active) {
            throw new CenterConflictException(
                    "center-slot-inactive",
                    "Créneau déjà désactivé",
                    "Ce créneau est déjà désactivé.");
        }
        this.active = false;
    }

    public boolean appliesOnDate(LocalDate date) {
        if (!active || date == null || date.getDayOfWeek() != dayOfWeek) {
            return false;
        }
        if (effectiveFrom != null && date.isBefore(effectiveFrom)) {
            return false;
        }
        return effectiveUntil == null || !date.isAfter(effectiveUntil);
    }

    private static DayOfWeek requireDayOfWeek(DayOfWeek day) {
        if (day == null) {
            throw new CenterValidationException(
                    "center-slot-invalid", "Jour obligatoire", "Le jour de la semaine du créneau est obligatoire.");
        }
        return day;
    }

    private static void validateTimes(LocalTime start, LocalTime end) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new CenterValidationException(
                    "center-slot-invalid",
                    "Horaires invalides",
                    "L'heure de fin du créneau doit être strictement postérieure à l'heure de début.");
        }
    }

    private static void validateDates(LocalDate from, LocalDate until) {
        if (from != null && until != null && until.isBefore(from)) {
            throw new CenterValidationException(
                    "center-slot-invalid",
                    "Dates d'effet invalides",
                    "La date de fin d'effet du créneau ne peut pas être antérieure à la date de début d'effet.");
        }
    }

    private static String normalizeOptional(String raw, int maxLen) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.length() > maxLen) {
            throw new CenterValidationException(
                    "center-slot-invalid",
                    "Créneau invalide",
                    "La longueur maximale autorisée (" + maxLen + " caractères) est dépassée.");
        }
        return trimmed;
    }

    public UUID getId() {
        return id;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public UUID getTeacherId() {
        return teacherId;
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public String getRoomName() {
        return roomName;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public LocalDate getEffectiveUntil() {
        return effectiveUntil;
    }

    public boolean isActive() {
        return active;
    }
}

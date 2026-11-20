package com.ecole.saas.academic.domain;

import com.ecole.saas.shared.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "class_rooms")
public class ClassRoom extends TenantScopedEntity {

    public static final int DEFAULT_CAPACITY = 40;
    public static final int MAX_CAPACITY = 500;

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9][A-Z0-9_-]{0,29}$");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "academic_year_id", nullable = false, updatable = false)
    private UUID academicYearId;

    @Column(name = "grade_level_id", nullable = false)
    private UUID gradeLevelId;

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "series", length = 30)
    private String series;

    @Column(name = "room_name", length = 60)
    private String roomName;

    @Column(name = "capacity", nullable = false)
    private int capacity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ClassRoomStatus status;

    protected ClassRoom() {
    }

    public ClassRoom(
            UUID academicYearId,
            UUID gradeLevelId,
            String code,
            String name,
            String series,
            String roomName,
            int capacity) {
        this.academicYearId = Objects.requireNonNull(academicYearId, "L'année scolaire est obligatoire.");
        this.gradeLevelId = Objects.requireNonNull(gradeLevelId, "Le niveau est obligatoire.");
        this.code = normalizeCode(code);
        this.name = requireName(name);
        this.series = normalizeOptional(series, 30, "La série");
        this.roomName = normalizeOptional(roomName, 60, "Le nom de la salle");
        this.capacity = validateCapacity(capacity);
        this.status = ClassRoomStatus.ACTIVE;
    }

    public void update(UUID newGradeLevelId, String newName, String newSeries, String newRoomName, int newCapacity) {
        assertActive();
        this.gradeLevelId = Objects.requireNonNull(newGradeLevelId, "Le niveau est obligatoire.");
        this.name = requireName(newName);
        this.series = normalizeOptional(newSeries, 30, "La série");
        this.roomName = normalizeOptional(newRoomName, 60, "Le nom de la salle");
        this.capacity = validateCapacity(newCapacity);
    }

    public void archive() {
        this.status = ClassRoomStatus.ARCHIVED;
    }

    public void reactivate() {
        this.status = ClassRoomStatus.ACTIVE;
    }

    public boolean isActive() {
        return this.status == ClassRoomStatus.ACTIVE;
    }

    public void assertActive() {
        if (this.status != ClassRoomStatus.ACTIVE) {
            throw new ClassRoomArchivedException("Une classe archivée ne peut plus être modifiée.");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getAcademicYearId() {
        return academicYearId;
    }

    public UUID getGradeLevelId() {
        return gradeLevelId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getSeries() {
        return series;
    }

    public String getRoomName() {
        return roomName;
    }

    public int getCapacity() {
        return capacity;
    }

    public ClassRoomStatus getStatus() {
        return status;
    }

    public static String normalizeCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new IllegalArgumentException("Le code de la classe est obligatoire.");
        }
        String normalized = rawCode.trim().toUpperCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "Le code de la classe doit contenir entre 1 et 30 caractères alphanumériques, tirets ou underscores.");
        }
        return normalized;
    }

    private static String requireName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            throw new IllegalArgumentException("Le nom de la classe est obligatoire.");
        }
        String trimmed = rawName.trim();
        if (trimmed.length() > 100) {
            throw new IllegalArgumentException("Le nom de la classe ne peut pas dépasser 100 caractères.");
        }
        return trimmed;
    }

    private static int validateCapacity(int capacity) {
        if (capacity <= 0 || capacity > MAX_CAPACITY) {
            throw new IllegalArgumentException(
                    "La capacité de la classe doit être comprise entre 1 et " + MAX_CAPACITY + " élèves.");
        }
        return capacity;
    }

    private static String normalizeOptional(String raw, int maxLength, String label) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(label + " ne peut pas dépasser " + maxLength + " caractères.");
        }
        return trimmed;
    }
}

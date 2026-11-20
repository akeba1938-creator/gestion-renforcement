package com.ecole.saas.academic.domain;

import com.ecole.saas.shared.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "class_subjects")
public class ClassSubject extends TenantScopedEntity {

    public static final int MAX_WEEKLY_HOURS = 40;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "class_room_id", nullable = false, updatable = false)
    private UUID classRoomId;

    @Column(name = "subject_id", nullable = false, updatable = false)
    private UUID subjectId;

    @Column(name = "coefficient", nullable = false)
    private int coefficient;

    @Column(name = "weekly_hours")
    private Integer weeklyHours;

    @Column(name = "mandatory", nullable = false)
    private boolean mandatory;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected ClassSubject() {
    }

    public ClassSubject(
            UUID classRoomId,
            UUID subjectId,
            int coefficient,
            Integer weeklyHours,
            boolean mandatory) {
        this.classRoomId = Objects.requireNonNull(classRoomId, "La classe est obligatoire.");
        this.subjectId = Objects.requireNonNull(subjectId, "La matière est obligatoire.");
        this.coefficient = Subject.validateCoefficient(coefficient);
        this.weeklyHours = validateWeeklyHours(weeklyHours);
        this.mandatory = mandatory;
        this.active = true;
    }

    public void update(int newCoefficient, Integer newWeeklyHours, boolean newMandatory) {
        this.coefficient = Subject.validateCoefficient(newCoefficient);
        this.weeklyHours = validateWeeklyHours(newWeeklyHours);
        this.mandatory = newMandatory;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClassRoomId() {
        return classRoomId;
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public int getCoefficient() {
        return coefficient;
    }

    public Integer getWeeklyHours() {
        return weeklyHours;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    public boolean isActive() {
        return active;
    }

    private static Integer validateWeeklyHours(Integer weeklyHours) {
        if (weeklyHours == null) {
            return null;
        }
        if (weeklyHours <= 0 || weeklyHours > MAX_WEEKLY_HOURS) {
            throw new IllegalArgumentException(
                    "Le volume horaire hebdomadaire doit être compris entre 1 et " + MAX_WEEKLY_HOURS + " heures.");
        }
        return weeklyHours;
    }
}

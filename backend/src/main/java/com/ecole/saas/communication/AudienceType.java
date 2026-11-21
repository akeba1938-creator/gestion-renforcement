package com.ecole.saas.communication;

/**
 * Types de cibles (audiences unitaires ou groupées) pour une campagne ou notification.
 */
public enum AudienceType {
    GUARDIAN(true),
    FAMILY(true),
    STUDENT(true),
    STUDENT_GUARDIANS(true),
    CLASS_ROOM_GUARDIANS(true),
    CLASS_ROOM_STUDENTS(true),
    CENTER_GROUP_GUARDIANS(true),
    CENTER_GROUP_STUDENTS(true),
    TEACHER(true),
    ALL_TEACHERS(false),
    STAFF_USER(true),
    ALL_STAFF(false),
    ALL_GUARDIANS(false);

    private final boolean requiresTargetId;

    AudienceType(boolean requiresTargetId) {
        this.requiresTargetId = requiresTargetId;
    }

    public boolean requiresTargetId() {
        return requiresTargetId;
    }
}

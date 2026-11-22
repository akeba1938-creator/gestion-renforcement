package com.ecole.saas.academic.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AcademicEntitiesDomainTest {

    @Test
    void shouldValidateGradeLevelRules() {
        GradeLevel level = new GradeLevel("6e", "Sixième", EducationCycle.MIDDLE_SCHOOL, 7);
        assertThat(level.getCode()).isEqualTo("6E");
        assertThat(level.isActive()).isTrue();

        level.update("Sixième (Collège)", EducationCycle.MIDDLE_SCHOOL, 8);
        level.deactivate();
        assertThat(level.isActive()).isFalse();
        level.activate();
        assertThat(level.isActive()).isTrue();

        assertThatThrownBy(() -> new GradeLevel("6E", "Sixième", EducationCycle.MIDDLE_SCHOOL, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldValidateSubjectRules() {
        Subject subject = new Subject("math", "Mathématiques", "Maths", SubjectCategory.SCIENTIFIC, 5);
        assertThat(subject.getCode()).isEqualTo("MATH");
        assertThat(subject.getDefaultCoefficient()).isEqualTo(5);
        assertThat(subject.isActive()).isTrue();

        assertThatThrownBy(() -> new Subject("MATH", "Mathématiques", null, SubjectCategory.SCIENTIFIC, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Subject("MATH", "Mathématiques", null, SubjectCategory.SCIENTIFIC, 51))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldValidateClassRoomAndClassSubjectRules() {
        UUID yearId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();
        ClassRoom room = new ClassRoom(yearId, levelId, "tle-s2-a", "Terminale S2 A", "S2", "Salle 14", 35);

        assertThat(room.getCode()).isEqualTo("TLE-S2-A");
        assertThat(room.getStatus()).isEqualTo(ClassRoomStatus.ACTIVE);

        room.archive();
        assertThat(room.isActive()).isFalse();
        assertThatThrownBy(() -> room.update(levelId, "Modifiée", "S2", "Salle 15", 40))
                .isInstanceOf(ClassRoomArchivedException.class);

        room.reactivate();
        assertThat(room.isActive()).isTrue();

        assertThatThrownBy(() -> room.update(levelId, "Réduite", "S2", "Salle 15", 10, 15))
                .isInstanceOf(ClassRoomCapacityConflictException.class);
        room.update(levelId, "Ajustée", "S2", "Salle 15", 15, 15);
        assertThat(room.getCapacity()).isEqualTo(15);

        assertThatThrownBy(() -> new ClassRoom(yearId, levelId, "6E-A", "6ème A", null, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClassRoom(yearId, levelId, "6E-A", "6ème A", null, null, 501))
                .isInstanceOf(IllegalArgumentException.class);

        ClassSubject classSubject = new ClassSubject(UUID.randomUUID(), UUID.randomUUID(), 6, 7, true);
        assertThat(classSubject.getCoefficient()).isEqualTo(6);
        assertThat(classSubject.getWeeklyHours()).isEqualTo(7);
        assertThat(classSubject.isMandatory()).isTrue();
        assertThat(classSubject.isActive()).isTrue();

        classSubject.deactivate();
        assertThat(classSubject.isActive()).isFalse();

        assertThatThrownBy(() -> new ClassSubject(UUID.randomUUID(), UUID.randomUUID(), 4, 41, true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

package com.ecole.saas.student;

import java.util.Set;
import java.util.UUID;

/**
 * Point d'extension (SPI) permettant à un module aval (notamment {@code center}) d'étendre
 * le périmètre d'élèves visibles d'un utilisateur {@code TUTOR} aux cohortes actives qui lui
 * sont affectées, sans créer de dépendance cyclique entre {@code student} et {@code center}.
 */
public interface TutorCohortStudentScopeProvider {

    Set<UUID> findActiveStudentIdsForTutorUser(UUID userId);
}

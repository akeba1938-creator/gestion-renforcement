package com.ecole.saas.academic;

import java.util.UUID;

/**
 * Point d'extension (SPI) permettant au module {@code student} d'exposer le nombre
 * d'inscriptions actives d'une classe sans créer de dépendance cyclique entre
 * {@code academic} et {@code student}.
 */
public interface ClassRoomEnrollmentCountProvider {

    long countActiveEnrollmentsByClassRoom(UUID classRoomId);
}

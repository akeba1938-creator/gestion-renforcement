package com.ecole.saas.school;

import java.util.Optional;
import java.util.UUID;

/**
 * Frontière publique du module {@code school} exposée aux autres modules (ex. {@code identity}).
 */
public interface SchoolDirectory {

    Optional<SchoolSummary> findSummaryByCode(String code);

    Optional<SchoolSummary> findSummaryById(UUID id);

    Optional<SchoolProfileSnapshot> findProfileById(UUID id);

    record SchoolSummary(UUID id, String code, String name, boolean active) {
    }
}

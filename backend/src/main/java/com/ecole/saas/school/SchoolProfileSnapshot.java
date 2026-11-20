package com.ecole.saas.school;

import java.util.UUID;

/**
 * Vue immuable du profil et des paramètres de base d'un établissement, exposée aux autres modules
 * sans fuite des entités internes du module {@code school}.
 */
public record SchoolProfileSnapshot(
        UUID id,
        String code,
        String name,
        String shortName,
        String schoolType,
        String status,
        boolean active,
        String timezone,
        String currency,
        String countryCode,
        String gradingPeriodSystem,
        int gradingScale) {
}

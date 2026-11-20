package com.ecole.saas.family;

import java.util.UUID;

public record FamilySummary(
        UUID id,
        String code,
        String name,
        String primaryPhone,
        String email,
        String city,
        String status,
        boolean active) {
}

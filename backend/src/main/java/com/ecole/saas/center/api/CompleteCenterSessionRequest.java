package com.ecole.saas.center.api;

import jakarta.validation.constraints.Size;

public record CompleteCenterSessionRequest(
        @Size(max = 200, message = "Le thème ne doit pas dépasser 200 caractères.")
        String topic,
        @Size(max = 500, message = "Les notes ne doivent pas dépasser 500 caractères.")
        String notes) {
}

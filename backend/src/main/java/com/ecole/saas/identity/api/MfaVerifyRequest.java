package com.ecole.saas.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MfaVerifyRequest(
        @NotBlank(message = "Le code MFA est obligatoire.")
        @Pattern(regexp = "\\d{6}", message = "Le code MFA doit contenir exactement 6 chiffres.")
        String code) {
}

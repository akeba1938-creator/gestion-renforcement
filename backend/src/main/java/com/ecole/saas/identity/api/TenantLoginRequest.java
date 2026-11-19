package com.ecole.saas.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TenantLoginRequest(
        @NotBlank(message = "Le code de l'établissement est obligatoire.")
        @Size(max = 32, message = "Le code de l'établissement est trop long.")
        String schoolCode,
        @NotBlank(message = "L'adresse e-mail est obligatoire.")
        @Email(message = "L'adresse e-mail n'est pas valide.")
        @Size(max = 255, message = "L'adresse e-mail est trop longue.")
        String email,
        @NotBlank(message = "Le mot de passe est obligatoire.")
        @Size(max = 128, message = "Le mot de passe est trop long.")
        String password) {
}

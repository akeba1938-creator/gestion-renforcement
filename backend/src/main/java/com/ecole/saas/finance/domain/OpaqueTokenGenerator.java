package com.ecole.saas.finance.domain;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Génère un jeton opaque cryptographiquement aléatoire pour la vérification des reçus (QR code).
 * Ne contient aucune donnée personnelle ni identifiant interne prédictible.
 */
public final class OpaqueTokenGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTE_LENGTH = 32;

    private OpaqueTokenGenerator() {
    }

    public static String generateVerificationToken() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

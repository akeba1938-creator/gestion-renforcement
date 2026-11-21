package com.ecole.saas.communication.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Calcul SHA-256 déterministe en domaine pur pour le contrôle d'intégrité des clés d'idempotence.
 */
public final class CommunicationHashSupport {

    private CommunicationHashSupport() {
    }

    public static String sha256Hex(String canonicalInput) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((canonicalInput != null ? canonicalInput : "").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Algorithme SHA-256 indisponible.", ex);
        }
    }
}

package com.ecole.saas.identity.domain;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.OptionalLong;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * TOTP selon RFC 6238 (HMAC-SHA1, 6 chiffres, pas de 30 secondes, tolérance de ±1 pas).
 */
public final class TotpGenerator {

    public static final int DIGITS = 6;
    public static final int PERIOD_SECONDS = 30;
    public static final int ALLOWED_DRIFT_STEPS = 1;
    public static final int SECRET_BYTES = 20;

    private static final Pattern CODE_PATTERN = Pattern.compile("\\d{" + DIGITS + "}");
    private static final int[] POWERS_OF_TEN = {1, 10, 100, 1_000, 10_000, 100_000, 1_000_000};

    private TotpGenerator() {
    }

    public static byte[] newSecret(SecureRandom random) {
        byte[] secret = new byte[SECRET_BYTES];
        random.nextBytes(secret);
        return secret;
    }

    public static long timeStep(Instant instant) {
        return Math.floorDiv(instant.getEpochSecond(), PERIOD_SECONDS);
    }

    public static String generate(byte[] secret, long timeStep) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(timeStep).array());
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int otp = binary % POWERS_OF_TEN[DIGITS];
            return String.format("%0" + DIGITS + "d", otp);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Calcul TOTP impossible.", e);
        }
    }

    /**
     * Vérifie un code TOTP en refusant tout pas de temps déjà utilisé (anti-rejeu).
     *
     * @return le pas de temps accepté, ou vide si le code est invalide ou rejoué.
     */
    public static OptionalLong verify(byte[] secret, String code, Instant now, Long lastUsedStep) {
        if (code == null || !CODE_PATTERN.matcher(code).matches()) {
            return OptionalLong.empty();
        }
        byte[] submitted = code.getBytes(StandardCharsets.US_ASCII);
        long current = timeStep(now);
        for (long step = current - ALLOWED_DRIFT_STEPS; step <= current + ALLOWED_DRIFT_STEPS; step++) {
            if (lastUsedStep != null && step <= lastUsedStep) {
                continue;
            }
            byte[] expected = generate(secret, step).getBytes(StandardCharsets.US_ASCII);
            if (MessageDigest.isEqual(expected, submitted)) {
                return OptionalLong.of(step);
            }
        }
        return OptionalLong.empty();
    }
}

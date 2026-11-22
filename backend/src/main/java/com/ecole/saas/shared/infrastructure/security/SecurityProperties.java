package com.ecole.saas.shared.infrastructure.security;

import java.util.Base64;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        @DefaultValue Jwt jwt,
        @DefaultValue Mfa mfa,
        @DefaultValue Cookie cookie,
        @DefaultValue Csrf csrf,
        @DefaultValue Cors cors) {

    public static final int MIN_JWT_SECRET_BYTES = 32;
    public static final int AES_256_KEY_BYTES = 32;

    public byte[] decodedJwtSecretBytes() {
        String encoded = jwt == null ? null : jwt.secret();
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException(
                    "La propriété app.security.jwt.secret (APP_JWT_SECRET) est obligatoire et doit contenir au moins 32 octets encodés en Base64.");
        }
        byte[] decoded = decodeBase64(encoded, "app.security.jwt.secret");
        if (decoded.length < MIN_JWT_SECRET_BYTES) {
            throw new IllegalStateException(
                    "La clé JWT app.security.jwt.secret doit contenir au moins 32 octets (256 bits) après décodage Base64.");
        }
        return decoded;
    }

    public byte[] decodedMfaEncryptionKeyBytes() {
        String encoded = mfa == null ? null : mfa.encryptionKey();
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException(
                    "La propriété app.security.mfa.encryption-key (APP_MFA_ENCRYPTION_KEY) est obligatoire et doit contenir exactement 32 octets encodés en Base64.");
        }
        byte[] decoded = decodeBase64(encoded, "app.security.mfa.encryption-key");
        if (decoded.length != AES_256_KEY_BYTES) {
            throw new IllegalStateException(
                    "La clé AES-256 app.security.mfa.encryption-key doit contenir exactement 32 octets (256 bits) après décodage Base64.");
        }
        return decoded;
    }

    private static byte[] decodeBase64(String encoded, String propertyName) {
        try {
            return Base64.getDecoder().decode(encoded.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "La propriété " + propertyName + " n'est pas encodée en Base64 valide.");
        }
    }

    public record Jwt(
            @DefaultValue("") String secret,
            @DefaultValue("ecole-saas") String issuer) {

        @Override
        public String toString() {
            return "Jwt[secret=***, issuer=" + issuer + "]";
        }
    }

    public record Mfa(@DefaultValue("") String encryptionKey) {

        @Override
        public String toString() {
            return "Mfa[encryptionKey=***]";
        }
    }

    public record Cookie(
            @DefaultValue("ecole_refresh_token") String refreshName,
            @DefaultValue("true") boolean secure,
            @DefaultValue("Strict") String sameSite,
            @DefaultValue("/api/v1/auth") String path) {
    }

    public record Csrf(
            @DefaultValue("XSRF-TOKEN") String cookieName,
            @DefaultValue("X-XSRF-TOKEN") String headerName) {
    }

    public record Cors(
            @DefaultValue("") List<String> allowedOrigins) {

        public List<String> normalizedAllowedOrigins() {
            if (allowedOrigins == null || allowedOrigins.isEmpty()) {
                return List.of();
            }
            return allowedOrigins.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(origin -> !origin.isBlank())
                    .distinct()
                    .toList();
        }
    }
}

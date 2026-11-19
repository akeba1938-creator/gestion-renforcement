package com.ecole.saas.identity.domain;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Encodage et décodage de la valeur opaque transportée dans le cookie HttpOnly de rafraîchissement.
 *
 * <p>Le préfixe distingue un compte d'établissement ({@code u.<tenantId>.<secret>}) d'un administrateur
 * SaaS ({@code p.<secret>}) afin d'ouvrir la transaction PostgreSQL RLS sur la bonne table/partition
 * avant de vérifier l'empreinte SHA-256 complète du jeton aléatoire 256 bits en base.
 */
public final class RefreshCookieCodec {

    private static final String USER_PREFIX = "u.";
    private static final String PLATFORM_PREFIX = "p.";
    private static final int MIN_SECRET_LENGTH = 32;

    private RefreshCookieCodec() {
    }

    public static EncodedRefreshCookie createForTenantUser(UUID tenantId, SecureRandom random) {
        Objects.requireNonNull(tenantId, "L'identifiant d'établissement est obligatoire.");
        String rawValue = USER_PREFIX + tenantId + "." + OpaqueTokens.generate(random);
        return new EncodedRefreshCookie(rawValue, OpaqueTokens.sha256Hex(rawValue));
    }

    public static EncodedRefreshCookie createForPlatformAdmin(SecureRandom random) {
        String rawValue = PLATFORM_PREFIX + OpaqueTokens.generate(random);
        return new EncodedRefreshCookie(rawValue, OpaqueTokens.sha256Hex(rawValue));
    }

    public static Optional<ParsedRefreshCookie> decode(String cookieValue) {
        if (cookieValue == null || cookieValue.isBlank()) {
            return Optional.empty();
        }
        if (cookieValue.startsWith(USER_PREFIX)) {
            String rest = cookieValue.substring(USER_PREFIX.length());
            int dot = rest.indexOf('.');
            if (dot <= 0 || dot >= rest.length() - 1) {
                return Optional.empty();
            }
            String tenantPart = rest.substring(0, dot);
            String secretPart = rest.substring(dot + 1);
            if (secretPart.length() < MIN_SECRET_LENGTH || secretPart.indexOf('.') >= 0) {
                return Optional.empty();
            }
            try {
                UUID tenantId = UUID.fromString(tenantPart);
                return Optional.of(new ParsedRefreshCookie(
                        SubjectType.USER,
                        tenantId,
                        OpaqueTokens.sha256Hex(cookieValue)));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }
        if (cookieValue.startsWith(PLATFORM_PREFIX)) {
            String secretPart = cookieValue.substring(PLATFORM_PREFIX.length());
            if (secretPart.length() < MIN_SECRET_LENGTH || secretPart.indexOf('.') >= 0) {
                return Optional.empty();
            }
            return Optional.of(new ParsedRefreshCookie(
                    SubjectType.PLATFORM_ADMIN,
                    null,
                    OpaqueTokens.sha256Hex(cookieValue)));
        }
        return Optional.empty();
    }

    public record EncodedRefreshCookie(String rawValue, String tokenHash) {
    }

    public record ParsedRefreshCookie(SubjectType subjectType, UUID tenantId, String tokenHash) {
    }
}

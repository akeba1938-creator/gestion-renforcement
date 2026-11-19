package com.ecole.saas.identity.infrastructure;

import com.ecole.saas.identity.domain.SecretCipher;
import com.ecole.saas.shared.infrastructure.security.SecurityProperties;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Chiffrement authentifié AES-256-GCM des secrets applicatifs (ex. secrets TOTP).
 * Génère un vecteur d'initialisation aléatoire de 96 bits (12 octets) à chaque chiffrement.
 */
@Component
public class AesGcmSecretCipher implements SecretCipher {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final String VERSION_PREFIX = "v1:";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int MIN_PAYLOAD_BYTES = IV_BYTES + (TAG_BITS / 8) + 1;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom;

    public AesGcmSecretCipher(SecurityProperties securityProperties) {
        byte[] keyBytes = securityProperties.decodedMfaEncryptionKeyBytes();
        if (keyBytes.length != KEY_BYTES) {
            throw new IllegalArgumentException("La clé AES-256 doit contenir exactement 32 octets.");
        }
        this.secretKey = new SecretKeySpec(keyBytes.clone(), "AES");
        this.secureRandom = new SecureRandom();
    }

    static AesGcmSecretCipher withKey(byte[] keyBytes) {
        if (keyBytes == null || keyBytes.length != KEY_BYTES) {
            throw new IllegalArgumentException("La clé AES-256 doit contenir exactement 32 octets.");
        }
        SecurityProperties properties = new SecurityProperties(
                null,
                new SecurityProperties.Mfa(Base64.getEncoder().encodeToString(keyBytes)),
                null,
                null);
        return new AesGcmSecretCipher(properties);
    }

    @Override
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            throw new IllegalArgumentException("La valeur à chiffrer ne peut pas être vide.");
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] payload = ByteBuffer.allocate(IV_BYTES + encrypted.length)
                    .put(iv)
                    .put(encrypted)
                    .array();
            return VERSION_PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Impossible de chiffrer le secret.", e);
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        if (ciphertext == null || !ciphertext.startsWith(VERSION_PREFIX)) {
            throw new IllegalStateException("Format de secret chiffré invalide.");
        }
        try {
            byte[] payload = Base64.getDecoder().decode(ciphertext.substring(VERSION_PREFIX.length()));
            if (payload.length < MIN_PAYLOAD_BYTES) {
                throw new IllegalStateException("Secret chiffré tronqué ou invalide.");
            }
            byte[] iv = new byte[IV_BYTES];
            byte[] encrypted = new byte[payload.length - IV_BYTES];
            System.arraycopy(payload, 0, iv, 0, IV_BYTES);
            System.arraycopy(payload, IV_BYTES, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            throw new IllegalStateException("Impossible de déchiffrer le secret.");
        }
    }
}

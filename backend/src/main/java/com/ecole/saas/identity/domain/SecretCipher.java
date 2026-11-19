package com.ecole.saas.identity.domain;

public interface SecretCipher {

    String encrypt(String plaintext);

    String decrypt(String ciphertext);
}

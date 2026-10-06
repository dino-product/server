package com.orbit.auth.adapter.out.persistence;

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
 * Apple refresh token을 AES-256-GCM으로 암호화한다. 저장 형식은 {@code v1:} + base64(IV 12바이트 + 암호문·태그)이고, 계정·클라이언트 문맥을 AAD로
 * 묶어 다른 행으로 옮긴 암호문은 복호화되지 않는다. 실패 메시지에 토큰·키를 남기지 않는다.
 */
@Component
class AppleRefreshTokenCipher {

    private static final String VERSION = "v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    AppleRefreshTokenCipher(AppleRefreshTokenEncryptionProperties properties) {
        this.key = new SecretKeySpec(properties.keyBytes(), "AES");
    }

    String encrypt(String plaintext, String context) {
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, iv, context);
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] stored = ByteBuffer.allocate(IV_BYTES + encrypted.length)
                    .put(iv)
                    .put(encrypted)
                    .array();
            return VERSION + Base64.getEncoder().encodeToString(stored);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot encrypt Apple refresh token");
        }
    }

    String decrypt(String stored, String context) {
        if (stored == null || !stored.startsWith(VERSION)) {
            throw new IllegalStateException("Unsupported Apple refresh token ciphertext");
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(stored.substring(VERSION.length()));
            if (bytes.length <= IV_BYTES) {
                throw new IllegalStateException("Cannot decrypt Apple refresh token");
            }
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            byte[] iv = new byte[IV_BYTES];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            return new String(cipher(Cipher.DECRYPT_MODE, iv, context).doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Cannot decrypt Apple refresh token");
        }
    }

    private Cipher cipher(int mode, byte[] iv, String context) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(mode, key, new GCMParameterSpec(TAG_BITS, iv));
        cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
        return cipher;
    }
}

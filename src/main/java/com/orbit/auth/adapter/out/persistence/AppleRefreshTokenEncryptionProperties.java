package com.orbit.auth.adapter.out.persistence;

import java.util.Base64;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Apple refresh token 저장 암호화 키(AES-256, base64로 적은 32바이트). 키 교체 절차는 암호문의 버전 접두사로 확장한다. */
@ConfigurationProperties(prefix = "app.auth.apple.refresh-token")
public record AppleRefreshTokenEncryptionProperties(String encryptionKey) {

    private static final int KEY_BYTES = 32;

    public AppleRefreshTokenEncryptionProperties {
        if (decode(encryptionKey).length != KEY_BYTES) {
            throw new IllegalArgumentException(
                    "app.auth.apple.refresh-token.encryption-key must be 32 bytes in base64");
        }
    }

    byte[] keyBytes() {
        return decode(encryptionKey);
    }

    private static byte[] decode(String value) {
        if (value == null) {
            return new byte[0];
        }
        try {
            return Base64.getDecoder().decode(value.trim());
        } catch (IllegalArgumentException exception) {
            return new byte[0];
        }
    }

    @Override
    public String toString() {
        return "AppleRefreshTokenEncryptionProperties[encryptionKey=***]";
    }
}

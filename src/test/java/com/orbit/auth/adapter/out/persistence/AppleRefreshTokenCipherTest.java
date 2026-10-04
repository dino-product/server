package com.orbit.auth.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Apple refresh token 암호화")
class AppleRefreshTokenCipherTest {

    private static final String KEY = randomKey();
    private static final AppleRefreshTokenCipher CIPHER =
            new AppleRefreshTokenCipher(new AppleRefreshTokenEncryptionProperties(KEY));

    @Test
    @DisplayName("암호화한 값을 같은 계정·클라이언트 문맥에서 복호화한다")
    void roundTripsWithinSameContext() {
        String encrypted = CIPHER.encrypt("apple-refresh-token", "7:com.orbit.app");

        assertThat(encrypted).startsWith("v1:").doesNotContain("apple-refresh-token");
        assertThat(CIPHER.decrypt(encrypted, "7:com.orbit.app")).isEqualTo("apple-refresh-token");
    }

    @Test
    @DisplayName("같은 값도 매번 다른 암호문이 된다")
    void usesFreshIvForEachEncryption() {
        assertThat(CIPHER.encrypt("token", "7:com.orbit.app")).isNotEqualTo(CIPHER.encrypt("token", "7:com.orbit.app"));
    }

    @Test
    @DisplayName("다른 계정·클라이언트 문맥으로 옮긴 암호문은 복호화하지 않는다")
    void rejectsCiphertextMovedToAnotherContext() {
        String encrypted = CIPHER.encrypt("token", "7:com.orbit.app");

        assertThatThrownBy(() -> CIPHER.decrypt(encrypted, "8:com.orbit.app"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("변조한 암호문이나 다른 키의 암호문은 복호화하지 않는다")
    void rejectsTamperedOrForeignCiphertext() {
        String encrypted = CIPHER.encrypt("token", "7:com.orbit.app");
        char last = encrypted.charAt(encrypted.length() - 1);
        String tampered = encrypted.substring(0, encrypted.length() - 1) + (last == 'A' ? 'B' : 'A');
        AppleRefreshTokenCipher other =
                new AppleRefreshTokenCipher(new AppleRefreshTokenEncryptionProperties(randomKey()));

        assertThatThrownBy(() -> CIPHER.decrypt(tampered, "7:com.orbit.app")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> other.decrypt(encrypted, "7:com.orbit.app")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("키는 base64로 적은 32바이트여야 한다")
    void requires256BitKey() {
        assertThatThrownBy(() -> new AppleRefreshTokenEncryptionProperties(
                        Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AppleRefreshTokenEncryptionProperties("not base64!"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static String randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}

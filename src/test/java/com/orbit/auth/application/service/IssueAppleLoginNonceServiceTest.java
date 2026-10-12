package com.orbit.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orbit.auth.application.port.in.command.dto.LoginNonceInfo;
import com.orbit.auth.application.port.out.AppleLoginNoncePort;
import com.orbit.auth.domain.HashedNonce;

@ExtendWith(MockitoExtension.class)
@DisplayName("Apple 로그인 nonce 발급")
class IssueAppleLoginNonceServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-04T01:00:00Z");

    @Mock
    private AppleLoginNoncePort nonces;

    @Test
    @DisplayName("무작위 raw nonce를 돌려주고 저장소에는 그 해시만 5분 만료로 보관한다")
    void storesOnlyHashOfIssuedRawNonce() {
        IssueAppleLoginNonceService service = new IssueAppleLoginNonceService(nonces, Clock.fixed(NOW, ZoneOffset.UTC));

        LoginNonceInfo first = service.issue();
        LoginNonceInfo second = service.issue();

        verify(nonces).store(eq(HashedNonce.fromRaw(first.nonce())), eq(IssueAppleLoginNonceService.NONCE_TTL));
        verify(nonces).store(eq(HashedNonce.fromRaw(second.nonce())), eq(IssueAppleLoginNonceService.NONCE_TTL));
        assertThat(first.nonce()).matches("[A-Za-z0-9_-]{43}").isNotEqualTo(second.nonce());
        assertThat(first.expiresAt()).isEqualTo(NOW.plus(IssueAppleLoginNonceService.NONCE_TTL));
    }
}

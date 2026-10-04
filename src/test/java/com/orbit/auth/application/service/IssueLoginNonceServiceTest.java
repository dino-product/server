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
import com.orbit.auth.application.port.out.LoginNoncePort;

@ExtendWith(MockitoExtension.class)
@DisplayName("로그인 nonce 발급")
class IssueLoginNonceServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-27T01:00:00Z");

    @Mock
    private LoginNoncePort nonces;

    @Test
    @DisplayName("무작위 nonce를 5분 만료로 보관하고 만료 시각과 함께 돌려준다")
    void issuesRandomNonceWithTtl() {
        IssueLoginNonceService service = new IssueLoginNonceService(nonces, Clock.fixed(NOW, ZoneOffset.UTC));

        LoginNonceInfo first = service.issue();
        LoginNonceInfo second = service.issue();

        verify(nonces).store(eq(first.nonce()), eq(IssueLoginNonceService.NONCE_TTL));
        verify(nonces).store(eq(second.nonce()), eq(IssueLoginNonceService.NONCE_TTL));
        assertThat(first.nonce()).isNotBlank().isNotEqualTo(second.nonce());
        assertThat(first.nonce()).matches("[A-Za-z0-9_-]{43}");
        assertThat(first.expiresAt()).isEqualTo(NOW.plus(IssueLoginNonceService.NONCE_TTL));
    }
}

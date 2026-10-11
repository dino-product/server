package com.orbit.auth.application.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orbit.auth.application.port.in.command.dto.RevokeAccountTokensCommand;
import com.orbit.auth.application.port.out.AccessTokenPort;
import com.orbit.auth.application.port.out.ClosedAccountPort;
import com.orbit.auth.domain.AccountId;

@ExtendWith(MockitoExtension.class)
@DisplayName("계정 단위 토큰 폐기")
class RevokeAccountTokensServiceTest {

    @Mock
    private ClosedAccountPort closedAccounts;

    @Mock
    private AccessTokenPort accessTokens;

    @InjectMocks
    private RevokeAccountTokensService service;

    @Test
    @DisplayName("닫힌 계정 표시를 Access Token 수명 동안 남겨 그 전에 발급한 토큰이 모두 만료될 때까지 거부하게 한다")
    void marksClosedForAccessTokenLifetime() {
        when(accessTokens.lifetime()).thenReturn(Duration.ofHours(1));

        service.revoke(new RevokeAccountTokensCommand(7L));

        verify(closedAccounts).markClosed(new AccountId(7L), Duration.ofHours(1));
    }
}

package com.orbit.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.orbit.auth.application.error.AuthErrorCode;
import com.orbit.auth.application.port.out.AccountRepository;
import com.orbit.auth.application.port.out.ProviderUnlinkRepository;
import com.orbit.auth.domain.Account;
import com.orbit.auth.domain.AccountClosed;
import com.orbit.auth.domain.AccountId;
import com.orbit.auth.domain.ExternalIdentity;
import com.orbit.auth.domain.OAuthProvider;
import com.orbit.auth.domain.ProviderUnlink;
import com.orbit.shared.error.BusinessException;

@ExtendWith(MockitoExtension.class)
@DisplayName("계정 폐쇄")
class AccountClosureServiceTest {

    private static final Instant REGISTERED_AT = Instant.parse("2026-09-27T01:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-11T03:00:00Z");
    private static final AccountId ACCOUNT_ID = new AccountId(7L);
    private static final ExternalIdentity KAKAO = new ExternalIdentity(OAuthProvider.KAKAO, "1234567890");

    @Mock
    private AccountRepository accounts;

    @Mock
    private ProviderUnlinkRepository providerUnlinks;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AccountClosureService service;

    @BeforeEach
    void setUp() {
        service =
                new AccountClosureService(accounts, providerUnlinks, eventPublisher, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("잠근 계정을 닫고 외부 식별마다 연결 해제할 일을 남긴 뒤 계정을 저장하고 폐쇄 사실을 알린다")
    void closesAccountAndKeepsProviderUnlinks() {
        Account account = Account.reconstitute(ACCOUNT_ID, List.of(KAKAO), REGISTERED_AT);
        when(accounts.findByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(account));

        service.close(ACCOUNT_ID.value());

        verify(providerUnlinks).saveAll(List.of(new ProviderUnlink(ACCOUNT_ID, KAKAO, NOW)));
        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accounts).update(saved.capture());
        assertThat(saved.getValue().closedAt()).contains(NOW);
        assertThat(saved.getValue().identities()).isEmpty();
        verify(eventPublisher).publishEvent(new AccountClosed(ACCOUNT_ID, NOW));
    }

    @Test
    @DisplayName("이미 닫힌 계정이면 아무것도 바꾸지 않고 끝낸다")
    void ignoresClosedAccount() {
        when(accounts.findByIdForUpdate(ACCOUNT_ID))
                .thenReturn(Optional.of(Account.reconstituteClosed(ACCOUNT_ID, REGISTERED_AT, NOW.minusSeconds(60))));

        service.close(ACCOUNT_ID.value());

        verify(providerUnlinks, never()).saveAll(any());
        verify(accounts, never()).update(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("계정이 없으면 AUTH-004로 거부한다")
    void rejectsMissingAccount() {
        when(accounts.findByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.close(ACCOUNT_ID.value()))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(AuthErrorCode.ACCOUNT_NOT_FOUND));
        verify(providerUnlinks, never()).saveAll(any());
    }
}

package com.orbit.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orbit.auth.application.error.AuthErrorCode;
import com.orbit.auth.application.port.in.query.dto.AccountInfo;
import com.orbit.auth.application.port.in.query.dto.GetAccountQuery;
import com.orbit.auth.application.port.out.AccountRepository;
import com.orbit.auth.domain.Account;
import com.orbit.auth.domain.AccountId;
import com.orbit.auth.domain.ExternalIdentity;
import com.orbit.auth.domain.OAuthProvider;
import com.orbit.shared.error.BusinessException;

@ExtendWith(MockitoExtension.class)
@DisplayName("계정 조회")
class GetAccountServiceTest {

    private static final Instant REGISTERED_AT = Instant.parse("2026-09-27T01:00:00Z");

    @Mock
    private AccountRepository accounts;

    @InjectMocks
    private GetAccountService service;

    @Test
    @DisplayName("저장된 계정의 식별자와 등록 시각을 돌려준다")
    void returnsStoredAccount() {
        when(accounts.findById(new AccountId(7L)))
                .thenReturn(Optional.of(Account.reconstitute(
                        new AccountId(7L), List.of(new ExternalIdentity(OAuthProvider.KAKAO, "1")), REGISTERED_AT)));

        assertThat(service.getAccount(new GetAccountQuery(7L))).isEqualTo(new AccountInfo(7L, REGISTERED_AT));
    }

    @Test
    @DisplayName("계정이 없으면 AUTH-004로 거부한다")
    void rejectsMissingAccount() {
        when(accounts.findById(new AccountId(404L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAccount(new GetAccountQuery(404L)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(AuthErrorCode.ACCOUNT_NOT_FOUND));
    }
}

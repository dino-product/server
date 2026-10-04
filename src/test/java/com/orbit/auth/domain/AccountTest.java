package com.orbit.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("계정")
class AccountTest {

    private static final ExternalIdentity KAKAO_IDENTITY = new ExternalIdentity(OAuthProvider.KAKAO, "1234567890");
    private static final Instant REGISTERED_AT = Instant.parse("2026-09-27T01:00:00Z");

    @Nested
    @DisplayName("등록")
    class Register {

        @Test
        @DisplayName("외부 식별 하나로 식별자 없는 계정을 만든다")
        void registersAccountWithIdentity() {
            Account account = Account.register(KAKAO_IDENTITY, REGISTERED_AT);

            assertThat(account.id()).isEmpty();
            assertThat(account.identities()).containsExactly(KAKAO_IDENTITY);
            assertThat(account.registeredAt()).isEqualTo(REGISTERED_AT);
        }

        @Test
        @DisplayName("외부 식별이 null이면 거부한다")
        void rejectsNullIdentity() {
            assertThatThrownBy(() -> Account.register(null, REGISTERED_AT))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("identity must not be null");
        }

        @Test
        @DisplayName("등록 시각이 null이면 거부한다")
        void rejectsNullRegisteredAt() {
            assertThatThrownBy(() -> Account.register(KAKAO_IDENTITY, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("registeredAt must not be null");
        }
    }

    @Nested
    @DisplayName("재구성")
    class Reconstitute {

        @Test
        @DisplayName("저장된 계정을 식별자와 함께 재구성하고 목록을 방어 복사한다")
        void reconstitutesAccount() {
            AccountId id = new AccountId(10L);
            List<ExternalIdentity> identities = new ArrayList<>(List.of(KAKAO_IDENTITY));

            Account account = Account.reconstitute(id, identities, REGISTERED_AT);
            identities.clear();

            assertThat(account.id()).contains(id);
            assertThat(account.identities()).containsExactly(KAKAO_IDENTITY);
            assertThatThrownBy(() -> account.identities().clear()).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("식별자가 null이면 거부한다")
        void rejectsNullId() {
            assertThatThrownBy(() -> Account.reconstitute(null, List.of(KAKAO_IDENTITY), REGISTERED_AT))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("id must not be null");
        }

        @Test
        @DisplayName("외부 식별이 없으면 거부한다")
        void rejectsEmptyIdentities() {
            assertThatThrownBy(() -> Account.reconstitute(new AccountId(1L), List.of(), REGISTERED_AT))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("identities must not be empty");
        }

        @Test
        @DisplayName("외부 식별에 null이 섞이면 거부한다")
        void rejectsNullIdentityElement() {
            List<ExternalIdentity> identities = Arrays.asList(KAKAO_IDENTITY, null);

            assertThatThrownBy(() -> Account.reconstitute(new AccountId(1L), identities, REGISTERED_AT))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("identities must not contain null");
        }

        @Test
        @DisplayName("같은 제공자의 외부 식별이 둘이면 거부한다")
        void rejectsDuplicateProvider() {
            List<ExternalIdentity> identities =
                    List.of(KAKAO_IDENTITY, new ExternalIdentity(OAuthProvider.KAKAO, "another"));

            assertThatThrownBy(() -> Account.reconstitute(new AccountId(1L), identities, REGISTERED_AT))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("identities must have at most one per provider");
        }
    }
}

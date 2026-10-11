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
    private static final ExternalIdentity APPLE_IDENTITY =
            new ExternalIdentity(OAuthProvider.APPLE, "001234.abcdef0123456789.0123");
    private static final Instant REGISTERED_AT = Instant.parse("2026-09-27T01:00:00Z");
    private static final Instant CLOSED_AT = Instant.parse("2026-10-11T03:00:00Z");

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

        @Test
        @DisplayName("닫힌 계정은 외부 식별 없이 폐쇄 시각과 함께 재구성한다")
        void reconstitutesClosedAccount() {
            Account account = Account.reconstituteClosed(new AccountId(10L), REGISTERED_AT, CLOSED_AT);

            assertThat(account.isClosed()).isTrue();
            assertThat(account.closedAt()).contains(CLOSED_AT);
            assertThat(account.identities()).isEmpty();
        }

        @Test
        @DisplayName("닫힌 계정의 폐쇄 시각이 없으면 거부한다")
        void rejectsClosedAccountWithoutClosedAt() {
            assertThatThrownBy(() -> Account.reconstituteClosed(new AccountId(10L), REGISTERED_AT, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("closedAt must not be null");
        }
    }

    @Nested
    @DisplayName("폐쇄")
    class Close {

        @Test
        @DisplayName("연결된 외부 식별을 모두 끊고 제공자마다 연결 해제할 일을 돌려준다")
        void releasesIdentitiesAsProviderUnlinks() {
            AccountId id = new AccountId(10L);
            Account account = Account.reconstitute(id, List.of(KAKAO_IDENTITY, APPLE_IDENTITY), REGISTERED_AT);

            List<ProviderUnlink> unlinks = account.close(CLOSED_AT);

            assertThat(unlinks)
                    .containsExactly(
                            new ProviderUnlink(id, KAKAO_IDENTITY, CLOSED_AT),
                            new ProviderUnlink(id, APPLE_IDENTITY, CLOSED_AT));
            assertThat(account.isClosed()).isTrue();
            assertThat(account.closedAt()).contains(CLOSED_AT);
            assertThat(account.identities()).isEmpty();
        }

        @Test
        @DisplayName("이미 닫힌 계정을 다시 닫으면 폐쇄 시각을 바꾸지 않고 할 일도 만들지 않는다")
        void closingTwiceChangesNothing() {
            Account account = Account.reconstitute(new AccountId(10L), List.of(KAKAO_IDENTITY), REGISTERED_AT);
            account.close(CLOSED_AT);

            assertThat(account.close(CLOSED_AT.plusSeconds(60))).isEmpty();
            assertThat(account.closedAt()).contains(CLOSED_AT);
        }

        @Test
        @DisplayName("저장되지 않은 계정은 닫을 수 없다")
        void rejectsUnsavedAccount() {
            Account account = Account.register(KAKAO_IDENTITY, REGISTERED_AT);

            assertThatThrownBy(() -> account.close(CLOSED_AT))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("only saved accounts can be closed");
        }

        @Test
        @DisplayName("폐쇄 시각이 없거나 등록 시각보다 앞서면 거부한다")
        void rejectsInvalidClosedAt() {
            Account account = Account.reconstitute(new AccountId(10L), List.of(KAKAO_IDENTITY), REGISTERED_AT);

            assertThatThrownBy(() -> account.close(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("closedAt must not be null");
            assertThatThrownBy(() -> account.close(REGISTERED_AT.minusSeconds(1)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("closedAt must not be before registeredAt");
            assertThat(account.isClosed()).isFalse();
        }

        @Test
        @DisplayName("연결 해제할 일의 문자열 표현에는 제공자 회원번호를 넣지 않는다")
        void hidesSubjectInProviderUnlinkText() {
            ProviderUnlink unlink = new ProviderUnlink(new AccountId(10L), KAKAO_IDENTITY, CLOSED_AT);

            assertThat(unlink.toString())
                    .doesNotContain(KAKAO_IDENTITY.subject())
                    .contains("KAKAO");
        }
    }
}

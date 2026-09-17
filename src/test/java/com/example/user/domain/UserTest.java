package com.example.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("사용자 도메인")
class UserTest {

    @Test
    @DisplayName("신규 사용자는 식별자 없이 생성된다")
    void registersUserWithoutId() {
        User user = User.register("홍길동");

        assertThat(user.id()).isEmpty();
        assertThat(user.displayName()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("저장된 사용자는 식별자와 함께 복원된다")
    void reconstitutesPersistedUser() {
        User user = User.reconstitute(new UserId(1L), "홍길동");

        assertThat(user.id()).contains(new UserId(1L));
    }

    @Test
    @DisplayName("빈 표시 이름은 거부한다")
    void rejectsBlankDisplayName() {
        assertThatThrownBy(() -> User.register(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("displayName must not be blank");
    }

    @Test
    void rejectsDisplayNameLongerThanOneHundredCharacters() {
        assertThatThrownBy(() -> User.register("a".repeat(101)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("displayName must be at most 100 characters");
    }

    @Test
    @DisplayName("사용자 식별자는 양수여야 한다")
    void rejectsNonPositiveId() {
        assertThatThrownBy(() -> new UserId(0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId must be positive");
    }
}

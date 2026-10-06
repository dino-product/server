package com.orbit.organization.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.orbit.shared.error.BusinessException;
import com.orbit.shared.error.CommonErrorCode;

@DisplayName("임시 요청자 계정 resolver")
class DenyingRequesterAccountResolverTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(DenyingRequesterAccountResolver.class);

    @Test
    @DisplayName("어떤 요청자도 확인하지 않고 권한 없음으로 거부한다")
    void deniesEveryRequester() {
        assertThatThrownBy(() -> new DenyingRequesterAccountResolver().resolve())
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(CommonErrorCode.FORBIDDEN));
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "test"})
    @DisplayName("로컬·테스트 프로필에서만 등록한다")
    void registersOnlyInLocalAndTest(String profile) {
        runner.withPropertyValues("spring.profiles.active=" + profile)
                .run(context -> assertThat(context).hasSingleBean(RequesterAccountResolver.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "prod,local", "prod,test", "staging", ""})
    @DisplayName("운영과 이름 없는·그 밖의 환경에서는 등록하지 않는다")
    void doesNotRegisterElsewhere(String profiles) {
        runner.withPropertyValues("spring.profiles.active=" + profiles)
                .run(context -> assertThat(context).doesNotHaveBean(RequesterAccountResolver.class));
    }
}

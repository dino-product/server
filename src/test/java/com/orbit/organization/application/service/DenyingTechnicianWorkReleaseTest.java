package com.orbit.organization.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.orbit.organization.TechnicianWorkRelease;

@DisplayName("schedule 구현 전 대기함 반환 임시 구현")
class DenyingTechnicianWorkReleaseTest {

    private final DenyingTechnicianWorkRelease release = new DenyingTechnicianWorkRelease();

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(DenyingTechnicianWorkRelease.class);

    @Test
    @DisplayName("역할 변경 반환을 성공한 척하지 않고 거부한다")
    void rejectsRoleChangeRelease() {
        assertThatThrownBy(() -> release.releaseForRoleChange(1L, 2L, 3L))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("비활성화 반환을 성공한 척하지 않고 거부한다")
    void rejectsDeactivationRelease() {
        assertThatThrownBy(() -> release.releaseForDeactivation(1L, 2L, 3L))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "test"})
    @DisplayName("로컬·테스트 프로필에서만 등록한다")
    void registersOnlyInLocalAndTest(String profile) {
        runner.withPropertyValues("spring.profiles.active=" + profile)
                .run(context -> assertThat(context).hasSingleBean(TechnicianWorkRelease.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "prod,local", "prod,test", "staging", ""})
    @DisplayName("운영과 이름 없는·그 밖의 환경에서는 등록하지 않는다")
    void doesNotRegisterElsewhere(String profiles) {
        runner.withPropertyValues("spring.profiles.active=" + profiles)
                .run(context -> assertThat(context).doesNotHaveBean(TechnicianWorkRelease.class));
    }
}

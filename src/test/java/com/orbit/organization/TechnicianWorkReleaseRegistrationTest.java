package com.orbit.organization;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.orbit.support.IntegrationTestSupport;

/**
 * 아직 이 요구 인터페이스를 주입받는 곳이 없어, schedule 구현을 더하면서 임시 구현을 지우지 않아도 다른 테스트는 실패하지 않는다. 그래서 전체 애플리케이션에서 구현이
 * 하나만 등록되는지 따로 확인한다.
 */
@DisplayName("대기함 반환 요구 인터페이스 등록")
class TechnicianWorkReleaseRegistrationTest extends IntegrationTestSupport {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("애플리케이션 전체에 구현이 하나만 등록된다")
    void registersExactlyOneImplementation() {
        assertThat(context.getBeansOfType(TechnicianWorkRelease.class)).hasSize(1);
    }
}

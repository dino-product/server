package com.orbit.organization;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.orbit.support.IntegrationTestSupport;

/** organization이 선언하고 schedule이 구현하는 요구 인터페이스라, 전체 애플리케이션에서 구현이 하나만 등록되는지 따로 확인한다. */
@DisplayName("작업 유형 삭제 요구 인터페이스 등록")
class WorkTypeReleaseRegistrationTest extends IntegrationTestSupport {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("애플리케이션 전체에 구현이 하나만 등록된다")
    void registersExactlyOneImplementation() {
        assertThat(context.getBeansOfType(WorkTypeRelease.class)).hasSize(1);
    }
}

package com.orbit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.support.IntegrationTestSupport;

@DisplayName("애플리케이션 컨텍스트")
class OrbitApplicationTests extends IntegrationTestSupport {

    @Test
    @DisplayName("Spring 컨텍스트가 정상적으로 시작된다")
    void contextLoads() {}
}

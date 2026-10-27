package com.orbit.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 전체 애플리케이션 통합 테스트의 공통 조립. MockMvc를 쓰지 않는 테스트도 같은 Spring 컨텍스트를 쓰도록 MockMvc 설정을 함께 올린다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
public abstract class IntegrationTestSupport implements SharedContainers {}

package com.orbit.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

import com.orbit.shared.internal.config.JpaAuditingConfig;
import com.orbit.shared.internal.config.TimeConfig;

/**
 * {@code @DataJpaTest} 슬라이스용 조립. 슬라이스는 애플리케이션 {@code @Configuration}을 읽지 않아 감사 컬럼 설정과 주입 {@code Clock}이 빠지므로, Entity를
 * 저장하는 어댑터 테스트는 Testcontainers와 함께 이 설정을 {@code @Import}한다. 다른 모듈의 테스트는 shared internal 타입을 직접 참조하지 않고 이 설정만 import한다.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class, TimeConfig.class})
public class PersistenceTestConfiguration {}

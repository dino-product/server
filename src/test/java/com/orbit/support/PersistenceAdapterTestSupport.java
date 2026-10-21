package com.orbit.support;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.orbit.shared.internal.config.JpaAuditingConfig;
import com.orbit.shared.internal.config.TimeConfig;

/**
 * 영속 어댑터 {@code @DataJpaTest}의 공통 조립. 상속한 테스트가 모두 같은 Spring 컨텍스트를 쓰도록 필요한 Bean을 한 번에 올린다.
 *
 * <ul>
 *   <li>모든 모듈의 {@code adapter.out.persistence} 패키지 Bean(어댑터·설정 속성). 어댑터는 모듈 밖에 공개하지 않는 타입이라 클래스 대신 패키지 이름으로
 *       포함한다. 테스트는 검증할 어댑터를 주입받거나 {@code EntityManager}로 직접 만든다. 이 패키지 패턴
 *       ({@code com.orbit.*.adapter.out.persistence}와 하위 패키지) 밖의 어댑터는 포함되지 않으며, 포함되는 어댑터는
 *       JPA·DataSource·{@code Clock}·설정 속성에만 의존해야 이 슬라이스에서 뜬다.
 *   <li>감사 컬럼 설정과 주입 {@code Clock}. 슬라이스는 애플리케이션 {@code @Configuration}을 읽지 않아 이 둘이 빠진다.
 * </ul>
 *
 * <p>감사 시각을 직접 움직여야 하는 테스트처럼 다른 Bean을 바꿔 끼우는 테스트는 상속하지 않고 따로 조립한다.
 */
@DataJpaTest(
        includeFilters =
                @ComponentScan.Filter(
                        type = FilterType.REGEX,
                        pattern = "com\\.orbit\\.\\w+\\.adapter\\.out\\.persistence\\..+"))
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, TimeConfig.class})
@Testcontainers(disabledWithoutDocker = true)
public abstract class PersistenceAdapterTestSupport implements SharedContainers {}

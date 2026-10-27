package com.orbit.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestExecutionListeners.MergeMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 테스트 JVM마다 PostgreSQL·Redis 컨테이너를 한 번만 띄워 모든 Spring 테스트 컨텍스트가 함께 쓰게 한다. 컨테이너가 필요한 테스트 클래스는 이 인터페이스를 구현한다.
 *
 * <p>컨테이너를 Bean이 아닌 정적 필드의 {@code @ServiceConnection}으로 연결한다. Bean으로 등록한 컨테이너는 그 컨텍스트가 닫힐 때 함께 멈춰 다른 컨텍스트가
 * 쓰던 컨테이너가 사라지기 때문이다. 처음 연결하는 컨텍스트가 컨테이너를 시작하고, 멈추고 지우는 일은 JVM이 끝난 뒤 Testcontainers(Ryuk)가 한다. 컨테이너 필드가
 * 같으면 컨텍스트 캐시 키도 같다.
 *
 * <p>컨텍스트마다 새 DB를 받지 않으므로 {@link SharedContainersCleanupListener}가 테스트 클래스를 시작하기 전에 DB 행과 Redis 키를 비운다.
 */
@TestExecutionListeners(listeners = SharedContainersCleanupListener.class, mergeMode = MergeMode.MERGE_WITH_DEFAULTS)
public interface SharedContainers {

    int REDIS_PORT = 6379;

    // 컨텍스트마다 커넥션 풀이 살아 있으므로 기본값(100)보다 넉넉히 둔다. fsync=off는 Testcontainers 기본 명령을 유지한 것이다.
    @ServiceConnection
    PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
            .withCommand("postgres", "-c", "fsync=off", "-c", "max_connections=300");

    @ServiceConnection(name = "redis")
    GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(REDIS_PORT);
}

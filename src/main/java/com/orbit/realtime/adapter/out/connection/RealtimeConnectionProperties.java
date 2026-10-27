package com.orbit.realtime.adapter.out.connection;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 인스턴스의 실시간 연결 관리 설정. 잘못된 값은 연결마다 실패하지 않도록 기동 시점에 거부한다.
 *
 * @param maxConnections 인스턴스가 동시에 유지하는 연결 수 상한. 연결마다 응답 버퍼와 전송 줄을 메모리에 두므로 인스턴스 메모리에 맞춘다. 없으면 2,000
 * @param maxConnectionsPerAccount 한 계정이 이 인스턴스에 유지하는 연결 수 상한. 넘으면 그 계정의 가장 오래된 연결을 닫는다. 없으면 5
 * @param heartbeatInterval 연결 유지 신호 간격. 앞단(CloudFront 오리진 응답 대기 30초)이 조용한 연결을 끊지 않도록 그보다 짧게 둔다. 없으면 15초
 * @param connectionLifetime 연결을 닫고 다시 연결하게 하는 수명. 연결마다 최대 10% 앞당겨 한꺼번에 다시 연결하지 않게 한다. 유지 신호 간격보다 길어야 하며
 *     없으면 30분
 * @param writeTimeout 쓰기 하나가 이보다 오래 막히면 그 연결을 목록에서 뺀다. 없으면 5초
 * @param deliveryThreads 연결에 쓰는 스레드 수. 막힌 쓰기 하나가 스레드 하나를 전송 수단의 쓰기 한도까지 붙잡는다. 없으면 8
 */
@ConfigurationProperties(prefix = "app.realtime.connection")
public record RealtimeConnectionProperties(
        Integer maxConnections,
        Integer maxConnectionsPerAccount,
        Duration heartbeatInterval,
        Duration connectionLifetime,
        Duration writeTimeout,
        Integer deliveryThreads) {

    public RealtimeConnectionProperties {
        maxConnections = maxConnections == null ? 2_000 : maxConnections;
        maxConnectionsPerAccount = maxConnectionsPerAccount == null ? 5 : maxConnectionsPerAccount;
        heartbeatInterval = heartbeatInterval == null ? Duration.ofSeconds(15) : heartbeatInterval;
        connectionLifetime = connectionLifetime == null ? Duration.ofMinutes(30) : connectionLifetime;
        writeTimeout = writeTimeout == null ? Duration.ofSeconds(5) : writeTimeout;
        deliveryThreads = deliveryThreads == null ? 8 : deliveryThreads;
        if (maxConnections < 1 || maxConnectionsPerAccount < 1 || deliveryThreads < 1) {
            throw new IllegalArgumentException("connection limits and delivery threads must be positive");
        }
        if (heartbeatInterval.toMillis() < 1 || writeTimeout.toMillis() < 1) {
            throw new IllegalArgumentException("heartbeatInterval and writeTimeout must be at least 1ms");
        }
        if (connectionLifetime.compareTo(heartbeatInterval) <= 0) {
            throw new IllegalArgumentException("connectionLifetime must be longer than heartbeatInterval");
        }
    }
}

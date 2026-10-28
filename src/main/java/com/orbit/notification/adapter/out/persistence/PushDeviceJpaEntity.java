package com.orbit.notification.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.orbit.notification.domain.PushDevice;
import com.orbit.notification.domain.PushPlatform;
import com.orbit.shared.persistence.BaseTimeEntity;

/**
 * 앱 푸시 기기. 계정은 auth 모듈의 식별자라 ID로만 둔다. 토큰마다 한 행이며, 등록은 PostgreSQL upsert로 하므로 이 엔티티는 조회에만 쓴다(감사 컬럼도 upsert가
 * 직접 채운다).
 */
@Entity
@Table(
        name = "push_devices",
        uniqueConstraints = @UniqueConstraint(name = PushDeviceJpaEntity.TOKEN_UNIQUE, columnNames = "token"))
class PushDeviceJpaEntity extends BaseTimeEntity {

    static final String TOKEN_UNIQUE = "uk_push_devices_token";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 20)
    private PushPlatform platform;

    @Column(name = "token", nullable = false, length = PushDevice.MAX_TOKEN_LENGTH)
    private String token;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    protected PushDeviceJpaEntity() {}

    PushDevice toDomain() {
        return PushDevice.reconstitute(id, accountId, platform, token, registeredAt);
    }
}

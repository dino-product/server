package com.orbit.auth.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** 계정·클라이언트별 Apple refresh token 암호문. 저장은 PostgreSQL upsert로 하므로 이 엔티티는 조회에만 쓴다. */
@Entity
@Table(
        name = "apple_refresh_tokens",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_apple_refresh_tokens_account_client",
                        columnNames = {"account_id", "client_id"}))
class AppleRefreshTokenJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private AccountJpaEntity account;

    @Column(name = "client_id", nullable = false, length = 255)
    private String clientId;

    @Column(name = "encrypted_token", nullable = false, length = 2048)
    private String encryptedToken;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AppleRefreshTokenJpaEntity() {}

    String clientId() {
        return clientId;
    }

    String encryptedToken() {
        return encryptedToken;
    }
}

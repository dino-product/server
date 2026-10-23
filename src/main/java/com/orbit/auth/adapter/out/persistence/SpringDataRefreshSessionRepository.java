package com.orbit.auth.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataRefreshSessionRepository extends JpaRepository<RefreshSessionJpaEntity, String> {

    /** 식별자를 발급 쪽이 정하므로 병합 조회 없이 바로 넣는다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            insert into refresh_sessions (id, account_id, token_hash, started_at, expires_at)
            values (:id, :accountId, :tokenHash, :startedAt, :expiresAt)
            """, nativeQuery = true)
    void insert(
            @Param("id") String id,
            @Param("accountId") Long accountId,
            @Param("tokenHash") String tokenHash,
            @Param("startedAt") Instant startedAt,
            @Param("expiresAt") Instant expiresAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RefreshSessionJpaEntity s where s.id = :id")
    Optional<RefreshSessionJpaEntity> findLockedById(@Param("id") String id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RefreshSessionJpaEntity s where s.id = :id")
    void deleteSession(@Param("id") String id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RefreshSessionJpaEntity s where s.id = :id"
            + " and (s.tokenHash = :tokenHash or s.previousTokenHash = :tokenHash)")
    void deleteSessionWithToken(@Param("id") String id, @Param("tokenHash") String tokenHash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RefreshSessionJpaEntity s where s.id = :id and s.accountId = :accountId")
    void deleteSessionOfAccount(@Param("id") String id, @Param("accountId") Long accountId);

    /**
     * 계정 폐쇄처럼 다른 Entity를 다루는 호출자 트랜잭션 안에서 부르므로, 호출자의 영속성 컨텍스트를 비우지 않는다. 그 트랜잭션에는 관리 중인 기기 세션이 없다.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from RefreshSessionJpaEntity s where s.accountId = :accountId")
    void deleteAllOfAccount(@Param("accountId") Long accountId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RefreshSessionJpaEntity s where s.accountId = :accountId and s.expiresAt <= :now")
    void deleteExpiredOfAccount(@Param("accountId") Long accountId, @Param("now") Instant now);
}

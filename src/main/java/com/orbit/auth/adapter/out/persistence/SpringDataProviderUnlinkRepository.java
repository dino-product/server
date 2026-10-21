package com.orbit.auth.adapter.out.persistence;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.orbit.auth.domain.OAuthProvider;

interface SpringDataProviderUnlinkRepository extends JpaRepository<ProviderUnlinkJpaEntity, Long> {

    @Query("select u from ProviderUnlinkJpaEntity u where u.accountId = :accountId")
    List<ProviderUnlinkJpaEntity> findByAccountId(@Param("accountId") Long accountId);

    @Query("select u from ProviderUnlinkJpaEntity u where u.nextAttemptAt <= :now order by u.nextAttemptAt, u.id")
    List<ProviderUnlinkJpaEntity> findDue(@Param("now") Instant now, Limit limit);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update ProviderUnlinkJpaEntity u
            set u.failedAttempts = :failedAttempts, u.nextAttemptAt = :nextAttemptAt
            where u.accountId = :accountId and u.provider = :provider
            """)
    int updateRetry(
            @Param("accountId") Long accountId,
            @Param("provider") OAuthProvider provider,
            @Param("failedAttempts") int failedAttempts,
            @Param("nextAttemptAt") Instant nextAttemptAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ProviderUnlinkJpaEntity u where u.accountId = :accountId and u.provider = :provider")
    int deleteByAccountAndProvider(@Param("accountId") Long accountId, @Param("provider") OAuthProvider provider);
}

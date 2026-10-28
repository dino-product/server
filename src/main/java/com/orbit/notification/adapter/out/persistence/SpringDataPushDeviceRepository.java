package com.orbit.notification.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataPushDeviceRepository extends JpaRepository<PushDeviceJpaEntity, Long> {

    /** 같은 토큰 행이 있으면 계정·플랫폼·등록 시각을 바꾸고 없으면 넣는다. 동시 등록에서도 유니크 제약 위반 없이 한 행만 남는다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            insert into push_devices (member_id, platform, token, registered_at, created_at, updated_at)
            values (:accountId, :platform, :token, :registeredAt, :registeredAt, :registeredAt)
            on conflict (token)
            do update set member_id = excluded.member_id, platform = excluded.platform,
                registered_at = excluded.registered_at, updated_at = excluded.updated_at
            """, nativeQuery = true)
    void upsert(
            @Param("accountId") Long accountId,
            @Param("platform") String platform,
            @Param("token") String token,
            @Param("registeredAt") Instant registeredAt);

    Optional<PushDeviceJpaEntity> findByToken(String token);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from PushDeviceJpaEntity d where d.id = :id and d.accountId = :accountId")
    int deleteOwnedBy(@Param("id") Long id, @Param("accountId") Long accountId);

    List<PushDeviceJpaEntity> findByAccountIdOrderById(Long accountId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from PushDeviceJpaEntity d where d.token = :token")
    int deleteWithToken(@Param("token") String token);
}

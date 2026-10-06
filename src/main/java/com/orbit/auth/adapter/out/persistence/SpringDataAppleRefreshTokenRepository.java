package com.orbit.auth.adapter.out.persistence;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataAppleRefreshTokenRepository extends JpaRepository<AppleRefreshTokenJpaEntity, Long> {

    /** 같은 계정·클라이언트 행이 있으면 바꾸고 없으면 넣는다. 동시 로그인에서도 유니크 제약 위반 없이 하나만 남는다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            insert into apple_refresh_tokens (account_id, client_id, encrypted_token, updated_at)
            values (:accountId, :clientId, :encryptedToken, :updatedAt)
            on conflict (account_id, client_id)
            do update set encrypted_token = excluded.encrypted_token, updated_at = excluded.updated_at
            """, nativeQuery = true)
    void upsert(
            @Param("accountId") Long accountId,
            @Param("clientId") String clientId,
            @Param("encryptedToken") String encryptedToken,
            @Param("updatedAt") Instant updatedAt);

    @Query("select t from AppleRefreshTokenJpaEntity t where t.account.id = :accountId")
    List<AppleRefreshTokenJpaEntity> findByAccountId(@Param("accountId") Long accountId);
}

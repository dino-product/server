package com.orbit.auth.application.port.out;

import java.time.Instant;
import java.util.Optional;

import com.orbit.auth.domain.AccountId;
import com.orbit.auth.domain.RefreshSession;
import com.orbit.auth.domain.RefreshSessionId;
import com.orbit.auth.domain.RefreshTokenHash;

/** 기기 세션을 보관한다. 토큰 원문은 저장하지 않고 해시만 둔다. */
public interface RefreshSessionRepository {

    /** 로그인으로 시작한 새 세션을 저장한다. */
    void saveNew(RefreshSession session);

    /** 같은 세션을 동시에 회전하는 트랜잭션이 기다리도록 잠그고 읽는다. 호출자 트랜잭션 안에서만 쓴다. */
    Optional<RefreshSession> findByIdForUpdate(RefreshSessionId id);

    /** {@link #findByIdForUpdate}로 잠그고 읽은 세션의 회전 상태를 같은 트랜잭션에서 저장한다. */
    void update(RefreshSession session);

    void delete(RefreshSessionId id);

    /** 그 계정의 세션일 때만 지운다. 다른 계정의 세션 식별자로는 아무것도 지우지 않는다. */
    void deleteOwnedBy(RefreshSessionId id, AccountId accountId);

    /** 비밀값 해시가 그 세션의 현재 또는 직전 토큰과 맞을 때만 지운다. 맞지 않으면 아무것도 지우지 않는다. */
    void deleteByToken(RefreshSessionId id, RefreshTokenHash tokenHash);

    void deleteAllByAccount(AccountId accountId);

    /** 그 계정에서 {@code now} 시각에 이미 만료된 세션을 지운다. */
    void deleteExpiredByAccount(AccountId accountId, Instant now);
}

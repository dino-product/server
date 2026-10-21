package com.orbit.auth.application.port.out;

import java.time.Instant;
import java.util.List;

import com.orbit.auth.domain.AccountId;

/** 계정 삭제 시 Apple 토큰 철회에 쓸 refresh token을 암호화해 보관한다. 계정·클라이언트마다 최신 값 하나만 둔다. */
public interface AppleRefreshTokenRepository {

    /** 같은 계정·클라이언트의 이전 값을 바꾸고, 없으면 새로 저장한다. 동시에 저장해도 하나만 남는다. */
    void save(AccountId accountId, String clientId, String refreshToken, Instant updatedAt);

    List<AppleRefreshToken> listByAccount(AccountId accountId);

    /** 철회했거나 Apple이 무효로 본 클라이언트의 값을 지운다. 없으면 아무것도 하지 않는다. */
    void delete(AccountId accountId, String clientId);

    /** 계정의 값을 모두 지운다. 철회를 포기한 닫힌 계정의 토큰을 더 보관하지 않을 때 쓴다. */
    void deleteByAccount(AccountId accountId);
}

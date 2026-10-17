package com.orbit.auth.domain;

import java.time.Instant;

/**
 * 닫힌 계정과 외부 제공자의 연결을 끊는 할 일. 카카오 연결 끊기는 회원번호가 필요하므로 계정에서 외부 식별을 지운 뒤에도 해제가 끝날 때까지 여기에 남긴다.
 * 문자열 표현에서 회원번호를 가린다.
 */
public record ProviderUnlink(AccountId accountId, ExternalIdentity identity, Instant requestedAt) {

    public ProviderUnlink {
        if (accountId == null) {
            throw new IllegalArgumentException("accountId must not be null");
        }
        if (identity == null) {
            throw new IllegalArgumentException("identity must not be null");
        }
        if (requestedAt == null) {
            throw new IllegalArgumentException("requestedAt must not be null");
        }
    }

    @Override
    public String toString() {
        return "ProviderUnlink[accountId=" + accountId.value() + ", provider=" + identity.provider() + ", requestedAt="
                + requestedAt + "]";
    }
}

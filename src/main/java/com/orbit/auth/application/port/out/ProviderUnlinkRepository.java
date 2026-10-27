package com.orbit.auth.application.port.out;

import java.time.Instant;
import java.util.List;

import com.orbit.auth.domain.AccountId;
import com.orbit.auth.domain.OAuthProvider;
import com.orbit.auth.domain.ProviderUnlink;

/**
 * 닫힌 계정의 제공자 연결 해제 대기를 해제가 끝나거나 포기할 때까지 보관한다. 대기는 계정·제공자로 식별하며, 여러 곳에서 같은 대기를 처리해도 결과가 같도록 이미 없는
 * 대기를 바꾸거나 지우는 것은 아무 일도 하지 않는다.
 */
public interface ProviderUnlinkRepository {

    void saveAll(List<ProviderUnlink> unlinks);

    List<ProviderUnlink> listByAccount(AccountId accountId);

    /** 다음 시도 시각이 지난 대기를 그 시각이 이른 순으로 최대 {@code limit}개 읽는다. */
    List<ProviderUnlink> listDue(Instant now, int limit);

    /** 같은 계정·제공자 대기의 실패 횟수와 다음 시도 시각을 바꾼다. */
    void update(ProviderUnlink unlink);

    void delete(AccountId accountId, OAuthProvider provider);
}

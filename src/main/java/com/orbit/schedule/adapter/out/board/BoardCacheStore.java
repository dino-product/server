package com.orbit.schedule.adapter.out.board;

import java.util.Optional;

import com.orbit.schedule.domain.OrganizationId;

/**
 * 조직별 진행 보드 캐시 저장소. 조직마다 조회 구간을 필드로 둔 값 하나와 세대 번호 하나를 둔다. 세대는 무효화마다 바뀌고, 저장은 저장하려는 보드를 만들기 전에 읽은
 * 세대가 그대로일 때만 한다. 그래서 무효화보다 먼저 읽은 데이터로 만든 보드는 저장되지 않는다. 명령이 실패하면 예외를 던진다.
 */
interface BoardCacheStore {

    /** 조직·구간 필드에 저장한 값. */
    Optional<String> find(OrganizationId organizationId, String period);

    /** 조직의 지금 세대. 없으면 다시 나오지 않을 값으로 새로 정한다. */
    long generation(OrganizationId organizationId);

    /** 세대가 {@code generation}과 같고 상한 안이면 저장한다. */
    StoreResult store(OrganizationId organizationId, String period, long generation, String value);

    /** 세대를 바꾸고 조직의 저장한 값을 모두 지운다. */
    void invalidate(OrganizationId organizationId);

    enum StoreResult {
        STORED,
        GENERATION_CHANGED,
        TOO_MANY_PERIODS
    }
}

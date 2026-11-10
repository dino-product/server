package com.orbit.schedule.application.port.out;

import java.time.Instant;
import java.util.function.Supplier;

import com.orbit.schedule.domain.OrganizationId;

/**
 * 진행 보드 스냅샷을 얻는 출력 포트. 같은 조직의 화면들이 실시간 신호마다 같은 보드를 한꺼번에 다시 조회하므로, 구현은 만든 스냅샷을 저장해 두고 나눠 쓸 수 있다.
 * 저장한 스냅샷은 그 조직의 작업이 바뀌면({@link #invalidate}) 다시 쓰지 않는다.
 */
public interface ProgressBoardSnapshotPort {

    /**
     * 조직·구간 [from, to)의 스냅샷. 저장해 둔 것이 없으면 {@code builder}로 만든다. builder는 호출한 스레드에서 부르며 자기 트랜잭션을 연다. 동시에 만드는 수가
     * 상한을 넘거나, 같은 스냅샷을 만드는 다른 요청을 기다리다 한도를 넘으면 {@link ProgressBoardBusyException}이다.
     */
    ProgressBoardSnapshot load(
            OrganizationId organizationId, Instant from, Instant to, Supplier<ProgressBoardSnapshot> builder);

    /** 조직의 저장된 스냅샷을 모두 버린다. 커밋된 변경 뒤, 화면에 다시 조회하라는 신호를 보내기 전에 부른다. 실패해도 던지지 않는다. */
    void invalidate(OrganizationId organizationId);
}

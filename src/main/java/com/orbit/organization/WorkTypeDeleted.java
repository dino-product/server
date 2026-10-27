package com.orbit.organization;

import java.time.Instant;
import java.util.List;

/**
 * 총관리자가 작업 유형을 삭제했다. 삭제와 같은 트랜잭션에서 발행되며, 소비 모듈은 커밋 뒤에 받는다. 이미 삭제된 유형을 다시 지우면 발행하지 않는다. 유형 없음이 된
 * 작업 중 담당 기사가 있는 작업(배정됨·작업전)은 그 기사에게 알린다([조직·계정] §7, [알림] §4). 알림 문구·설정·묶음 발송은 소비자가 정한다(NTF-004).
 *
 * @param releasedWorks 유형 없음이 된 시작 전 작업. 대기함 작업은 담당 기사가 null이다
 * @param occurredAt 삭제 시각
 */
public record WorkTypeDeleted(
        Long organizationId, Long workTypeId, List<WorkTypeReleasedWork> releasedWorks, Instant occurredAt) {

    public WorkTypeDeleted {
        releasedWorks = List.copyOf(releasedWorks);
    }
}

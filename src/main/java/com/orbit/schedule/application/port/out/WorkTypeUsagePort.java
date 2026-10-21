package com.orbit.schedule.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.WorkStatus;
import com.orbit.schedule.domain.WorkTypeId;

/** 작업 유형을 쓰는 작업의 상태별 건수와, 작업 유형 삭제로 인한 유형 해제. 조직 범위이고 인자는 null이 아니다. */
public interface WorkTypeUsagePort {

    /** 그 유형을 쓰는 작업 수를 상태별로 센다. 작업이 없는 상태는 빠진다. */
    Map<WorkStatus, Long> countByStatus(OrganizationId organizationId, WorkTypeId workTypeId);

    /**
     * 그 유형을 쓰면서 지정한 상태인 작업의 유형을 한 문장으로 비우고, 버전을 올리고 수정 시각을 {@code at}으로 둔 뒤 비운 작업을 돌려준다. 다른 트랜잭션이 바꾸는
     * 중인 작업은 그 커밋을 기다린 뒤 바뀐 상태로 다시 판단한다. 순서는 보장하지 않는다.
     */
    List<ClearedWorkType> clearWorkType(
            OrganizationId organizationId, WorkTypeId workTypeId, Set<WorkStatus> statuses, Instant at);
}

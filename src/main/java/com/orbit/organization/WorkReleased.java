package com.orbit.organization;

import java.util.List;

/** 대기함으로 돌린 작업 ID. 예정 작업이 없었으면 비어 있다. */
public record WorkReleased(List<Long> releasedWorkIds) implements TechnicianWorkReleaseResult {

    public WorkReleased {
        releasedWorkIds = WorkIdList.copyOf(releasedWorkIds, "releasedWorkIds");
    }
}

package com.orbit.organization;

import java.util.List;

/** 작업중 작업이 있어 아무것도 바꾸지 않았다. 그 작업 ID를 하나 이상 담는다. */
public record WorkReleaseBlocked(List<Long> inProgressWorkIds) implements TechnicianWorkReleaseResult {

    public WorkReleaseBlocked {
        inProgressWorkIds = WorkIdList.copyOf(inProgressWorkIds, "inProgressWorkIds");
        if (inProgressWorkIds.isEmpty()) {
            throw new IllegalArgumentException("inProgressWorkIds must not be empty");
        }
    }
}

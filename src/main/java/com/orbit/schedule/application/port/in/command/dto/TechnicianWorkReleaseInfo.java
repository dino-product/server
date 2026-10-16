package com.orbit.schedule.application.port.in.command.dto;

import java.util.List;

/**
 * 대기함 반환 결과. 돌렸으면(released=true) 돌린 작업 식별자를, 작업중 작업 때문에 아무것도 바꾸지 않았으면(released=false) 그 작업중 작업 식별자를 오름차순으로
 * 담는다. 돌린 결과는 비어 있을 수 있지만 막힌 결과에는 작업중 작업이 반드시 있다.
 */
public record TechnicianWorkReleaseInfo(boolean released, List<Long> workIds) {

    public TechnicianWorkReleaseInfo {
        if (workIds == null) {
            throw new IllegalArgumentException("workIds must not be null");
        }
        workIds = List.copyOf(workIds);
        if (!released && workIds.isEmpty()) {
            throw new IllegalArgumentException("blocked result must have in-progress works");
        }
    }

    public static TechnicianWorkReleaseInfo released(List<Long> releasedWorkIds) {
        return new TechnicianWorkReleaseInfo(true, releasedWorkIds);
    }

    public static TechnicianWorkReleaseInfo blocked(List<Long> inProgressWorkIds) {
        return new TechnicianWorkReleaseInfo(false, inProgressWorkIds);
    }
}

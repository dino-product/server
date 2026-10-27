package com.orbit.organization.application.port.in.query.dto;

import com.orbit.organization.domain.WorkType;

/** 삭제되지 않은 작업 유형. 삭제된 유형은 조회 결과에 나오지 않는다. */
public record WorkTypeInfo(Long workTypeId, String name, String color) {

    public static WorkTypeInfo from(WorkType workType) {
        return new WorkTypeInfo(
                workType.id()
                        .orElseThrow(() -> new IllegalArgumentException("workType must have an id"))
                        .value(),
                workType.name().value(),
                workType.color().value());
    }
}

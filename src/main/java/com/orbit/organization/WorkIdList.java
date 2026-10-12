package com.orbit.organization;

import java.util.List;

/** 대기함 반환 결과가 담는 작업 ID 목록의 공통 검사. */
final class WorkIdList {

    private WorkIdList() {}

    /** null이거나 양수가 아닌 ID가 없는지 확인하고 바꿀 수 없는 복사본을 돌려준다. */
    static List<Long> copyOf(List<Long> workIds, String name) {
        if (workIds == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        for (Long workId : workIds) {
            if (workId == null || workId <= 0) {
                throw new IllegalArgumentException(name + " must contain only positive ids");
            }
        }
        return List.copyOf(workIds);
    }
}

package com.orbit.schedule.application.port.in.query.dto;

import java.time.Instant;
import java.util.List;

import com.orbit.schedule.domain.AssignmentEndReason;
import com.orbit.schedule.domain.AssignmentResult;
import com.orbit.schedule.domain.WorkStatus;

/**
 * 한 기사의 작업 이력과 통계. 구간과 그 기사의 배정 일정이 겹치는 작업을 지금 상태와 관계없이 담는다(다른 기사로 바뀌었거나 거절·취소된 작업 포함).
 *
 * @param technicianId 조회한 기사(기사가 비워 보냈으면 본인)
 * @param works 그 기사의 마지막 배정 시작시각 내림차순(최근 먼저), 작업 식별자 내림차순으로 정렬한 작업
 */
public record WorkHistoryInfo(Long technicianId, List<AssignedWork> works, Statistics statistics) {

    public WorkHistoryInfo {
        works = List.copyOf(works);
    }

    /**
     * 기사가 배정받았던 작업 하나와 그 작업에서 기사의 마지막 배정(구간과 겹치는 것 중). 되돌린 취소도 배정 이력에는 취소 종료로 남는다.
     *
     * @param status 작업의 지금 상태(다른 기사로 넘어간 작업이면 그 뒤의 상태)
     * @param current 이 배정이 작업의 마지막 배정이고 작업에 그 일정이 남아 있는지(완료·취소된 작업도 마지막 담당이면 true). 구간 밖에서 일정이 바뀌었거나
     *     다시 배정됐으면 false
     * @param assignmentNumber 이 배정의 순번(1부터). 지금 배정이 아닐 수 있으므로 수락·거절 등에는 current가 true일 때만 쓴다
     * @param result 그 배정의 결과
     * @param endReason 그 배정이 관리자 조치로 끝났으면 방식. 아니면 null
     */
    public record AssignedWork(
            Long workId,
            String name,
            WorkStatus status,
            boolean current,
            int assignmentNumber,
            Instant startTime,
            Instant endTime,
            AssignmentResult result,
            AssignmentEndReason endReason) {}

    /**
     * 이력의 작업 수 집계.
     *
     * @param assigned 이력의 모든 작업 수
     * @param completed current인 채 완료된 작업 수. 완료 시각이 아니라 배정 일정이 속한 구간에서 센다
     * @param rejected 그 기사가 구간 안에서 한 번이라도 거절한 작업 수
     * @param cancelled current인 채 취소된 작업 수
     */
    public record Statistics(int assigned, int completed, int rejected, int cancelled) {}
}

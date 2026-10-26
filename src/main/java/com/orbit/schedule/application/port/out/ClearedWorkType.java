package com.orbit.schedule.application.port.out;

import com.orbit.schedule.domain.TechnicianId;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkStatus;

/**
 * 유형을 비운 작업.
 *
 * @param workId 작업 식별자
 * @param status 비울 때의 상태
 * @param technicianId 작업 행의 기사 칸. 배정됨·작업전이면 담당 기사, 등록(대기함)이면 미리 입력한 담당기사나 마지막 배정 기사이며 없으면 null
 */
public record ClearedWorkType(WorkId workId, WorkStatus status, TechnicianId technicianId) {}

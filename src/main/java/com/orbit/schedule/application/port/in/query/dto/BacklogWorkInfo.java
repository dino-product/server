package com.orbit.schedule.application.port.in.query.dto;

import java.time.Duration;
import java.time.Instant;

import com.orbit.schedule.domain.RejectionReason;

/**
 * 대기함 작업 카드 하나. 타임테이블의 대기함과 작업 목록의 대기함 요약이 함께 쓴다.
 *
 * @param workType 작업유형. 없으면 null
 * @param registrar 등록자
 * @param registeredAt 등록 시각
 * @param plannedTechnician 등록 때 미리 입력한 담당기사. 없으면 null
 * @param plannedStartTime 등록 때 미리 입력한 시작시각. 없으면 null
 * @param plannedExpectedDuration 등록 때 미리 입력한 예상소요시간. 없으면 null
 * @param technicianMissing 담당기사가 정해지지 않았는지
 * @param timeMissing 시작시각이나 예상소요시간이 정해지지 않았는지
 * @param returnedByRejection 기사가 거절해 돌아온 작업인지
 * @param rejectionReason 돌아오게 한 거절의 사유. 거절로 돌아온 작업이 아니면 null
 * @param rejectionNote 그 거절의 메모(기타 사유). 없으면 null
 * @param stale 조회 시각에 대기함에서 방치된 작업인지(등록한 지 72시간 이상)
 * @param version 작업 버전. 배정 요청의 expectedVersion으로 그대로 보낸다
 */
public record BacklogWorkInfo(
        Long workId,
        String name,
        LabelInfo workType,
        StaffInfo registrar,
        Instant registeredAt,
        TechnicianInfo plannedTechnician,
        Instant plannedStartTime,
        Duration plannedExpectedDuration,
        boolean technicianMissing,
        boolean timeMissing,
        boolean returnedByRejection,
        RejectionReason rejectionReason,
        String rejectionNote,
        boolean stale,
        long version) {}

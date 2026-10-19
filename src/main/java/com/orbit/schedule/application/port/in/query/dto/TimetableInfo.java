package com.orbit.schedule.application.port.in.query.dto;

import java.time.Instant;
import java.util.List;

import com.orbit.schedule.domain.WorkStatus;

/**
 * 타임테이블. 기사 행, 구간과 일정이 겹치는 기사별 작업 칸, 대기함 작업 목록이다.
 *
 * @param technicians 기사 행. 그 조직의 활성 기사를 이름(없으면 맨 뒤)·기사 계약 식별자 순으로 담는다
 * @param scheduledWorks 기사 식별자, 시작시각, 작업 식별자 순으로 정렬한 작업 칸. 수락대기·수락됨·작업중·완료 작업만 담는다. 기사 행에 없는 기사(비활성화된
 *     기사의 작업중·완료 작업 등)의 칸도 담는다
 * @param backlog 등록 시각, 작업 식별자 순(오래된 순)으로 정렬한 대기함 작업
 */
public record TimetableInfo(
        List<TechnicianInfo> technicians, List<ScheduledWork> scheduledWorks, List<BacklogWorkInfo> backlog) {

    public TimetableInfo {
        technicians = List.copyOf(technicians);
        scheduledWorks = List.copyOf(scheduledWorks);
        backlog = List.copyOf(backlog);
    }

    /**
     * 기사 일정의 작업 칸 하나.
     *
     * @param technician 담당 기사
     * @param delayed 조회 시각에 지연된 작업인지
     * @param conflicting 같은 기사의 다른 활성 작업과 일정이 겹치는지(확인하고 겹침을 허용한 배정). 겹치는 짝이 구간 밖에 있거나 유형 조건으로 빠져도 표시한다
     * @param workType 작업유형. 없으면 null
     * @param registrar 등록자
     * @param version 작업 버전. 배정 변경·해제 요청의 expectedVersion으로 그대로 보낸다
     */
    public record ScheduledWork(
            Long workId,
            String name,
            WorkStatus status,
            TechnicianInfo technician,
            Instant startTime,
            Instant endTime,
            boolean delayed,
            boolean conflicting,
            LabelInfo workType,
            StaffInfo registrar,
            long version) {}
}

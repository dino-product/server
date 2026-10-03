package com.orbit.schedule.application.port.in.query.dto;

import java.time.Instant;
import java.util.List;

import com.orbit.schedule.domain.WorkStatus;

/**
 * 진행 보드. 구간과 일정이 겹치는 작업을 상태별 열로 나누고 진행 현황을 센다.
 *
 * @param columns 수락대기·수락됨·작업중·완료 순서의 열. 작업이 없는 상태도 빈 열로 담는다
 */
public record ProgressBoardInfo(List<Column> columns, Summary summary) {

    public ProgressBoardInfo {
        columns = List.copyOf(columns);
    }

    /**
     * 한 상태의 열.
     *
     * @param cards 시작시각, 작업 식별자 순으로 정렬한 작업 카드
     */
    public record Column(WorkStatus status, List<Card> cards) {

        public Column {
            cards = List.copyOf(cards);
        }
    }

    /**
     * 작업 카드 하나.
     *
     * @param delayed 조회 시각에 지연된 작업인지
     */
    public record Card(
            Long workId, String name, Long technicianId, Instant startTime, Instant endTime, boolean delayed) {}

    /**
     * 보드의 작업 수 집계.
     *
     * @param total 보드의 모든 작업 수
     * @param completed 완료된 작업 수
     * @param inProgress 작업중인 작업 수
     * @param delayed 지연된 작업 수(수락대기·수락됨·작업중 중)
     */
    public record Summary(int total, int completed, int inProgress, int delayed) {}
}

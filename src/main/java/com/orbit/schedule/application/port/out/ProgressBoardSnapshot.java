package com.orbit.schedule.application.port.out;

import java.time.Instant;
import java.util.List;

import com.orbit.schedule.application.port.in.query.dto.TechnicianInfo;
import com.orbit.schedule.domain.WorkStatus;

/**
 * 한 조직·구간의 진행 보드 카드. 조회 시각과 상관없는 값만 담아, 저장해 두고 나중 시각의 지연 여부를 다시 판정할 수 있다. 카드 순서에는 의미가 없다.
 *
 * @param cards 보일 일정이 구간과 겹치는 작업의 카드
 */
public record ProgressBoardSnapshot(List<Card> cards) {

    public ProgressBoardSnapshot {
        cards = List.copyOf(cards);
    }

    /**
     * 작업 카드 하나.
     *
     * @param technician 담당 기사. 없으면 null
     * @param endTime 예정 종료시각. 없으면 null
     * @param customerName 고객 이름. 없으면 null
     * @param delayThreshold 이 시각부터 지연이다({@link com.orbit.schedule.domain.Work#delayThreshold}). 지연이 아니면 null
     */
    public record Card(
            Long workId,
            WorkStatus status,
            String name,
            TechnicianInfo technician,
            Instant startTime,
            Instant endTime,
            String customerName,
            boolean returnedByRejection,
            Instant delayThreshold) {

        /** 주어진 시각에 지연된 카드인지. {@link com.orbit.schedule.domain.Work#isDelayedAt}과 같은 판정이다. */
        public boolean delayedAt(Instant now) {
            return delayThreshold != null && !now.isBefore(delayThreshold);
        }
    }
}

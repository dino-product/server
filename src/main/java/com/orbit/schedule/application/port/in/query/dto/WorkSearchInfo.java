package com.orbit.schedule.application.port.in.query.dto;

import java.time.Instant;
import java.util.List;

import com.orbit.schedule.domain.WorkStatus;

/**
 * 작업 목록 검색 결과 한 쪽.
 *
 * @param works 요청한 정렬 순서의 작업. 쪽이 끝을 넘으면 비어 있다
 * @param totalCount 조건에 맞는 전체 작업 수. 쪽과 관계없다
 * @param page 적용한 쪽 번호(0부터)
 * @param size 적용한 한 쪽의 작업 수
 */
public record WorkSearchInfo(List<WorkSummary> works, long totalCount, int page, int size) {

    public WorkSearchInfo {
        works = List.copyOf(works);
    }

    /**
     * 목록의 작업 한 줄.
     *
     * @param technician 작업에 보일 일정({@link com.orbit.schedule.domain.Work#shownSchedule})의 담당 기사. 대기함 작업은 미리 입력한
     *     담당기사나 마지막 배정의 기사다. 없으면(담당기사를 정하지 않은 대기함 작업, 배정 전에 취소된 작업) null
     * @param startTime 작업에 보일 일정의 시작시각. 없으면 null
     * @param endTime 작업에 보일 일정의 종료시각. 시작시각이나 예상소요시간이 없으면 null
     * @param customerName 고객 이름. 없으면 null
     * @param workType 작업유형. 없으면 null
     * @param registrar 등록자
     * @param registeredAt 등록 시각
     * @param lastModifiedAt 작업을 마지막으로 저장한 시각(최근 수정일)
     * @param delayed 조회 시각에 지연된 작업인지
     * @param returnedByRejection 기사가 거절해 대기함으로 돌아온 작업인지
     */
    public record WorkSummary(
            Long workId,
            String name,
            WorkStatus status,
            TechnicianInfo technician,
            Instant startTime,
            Instant endTime,
            String customerName,
            LabelInfo workType,
            StaffInfo registrar,
            Instant registeredAt,
            Instant lastModifiedAt,
            boolean delayed,
            boolean returnedByRejection) {}
}

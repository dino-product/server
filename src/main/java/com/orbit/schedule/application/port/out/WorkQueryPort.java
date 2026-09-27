package com.orbit.schedule.application.port.out;

import java.time.Instant;
import java.util.List;

import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.Work;

/**
 * 화면 조회용 작업 목록 출력 포트. 변경에 쓰는 {@link WorkRepository}와 달리 여러 작업을 조건으로 읽기만 한다. 조회는 모두 조직 범위이고, 결과는 영속 상태와
 * 분리된 사본이다. 순서를 정하는 검색 말고는 순서를 보장하지 않으므로 표시 순서는 호출자가 정한다. 인자는 null이 아니다.
 *
 * <p>TODO(HM-234): 지금은 빈 목록을 돌려주는 임시 구현뿐이다. JPA 어댑터에서 일정 시각·상태 인덱스로 구현한다.
 */
public interface WorkQueryPort {

    /**
     * 현재 일정이 [from, to) 구간과 겹치는 작업. 상태와 관계없이 일정이 있는 작업(수락대기·수락됨·작업중·완료, 배정 중 취소된 작업)이 모두 후보이며, 무엇을
     * 보여 줄지는 호출자가 거른다.
     */
    List<Work> listScheduledBetween(OrganizationId organizationId, Instant from, Instant to);

    /** 대기함 작업(등록 상태). */
    List<Work> listBacklog(OrganizationId organizationId);

    /** 조건에 맞는 작업의 한 쪽과 전체 수. 이 메서드는 조건의 정렬 순서를 지킨다. */
    WorkSearchResult search(OrganizationId organizationId, WorkSearchCriteria criteria);
}

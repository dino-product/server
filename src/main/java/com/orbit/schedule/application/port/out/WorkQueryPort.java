package com.orbit.schedule.application.port.out;

import java.time.Instant;
import java.util.List;

import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.Work;

/**
 * 화면 조회용 작업 목록 출력 포트. 변경에 쓰는 {@link WorkRepository}와 달리 여러 작업을 조건으로 읽기만 한다. 조회는 모두 조직 범위이고, 결과는 영속 상태와
 * 분리된 사본이다. 순서를 정하는 검색 말고는 순서를 보장하지 않으므로 표시 순서는 호출자가 정한다. 인자는 null이 아니다.
 *
 * <p>TODO(HM-234): 지금은 빈 목록을 돌려주는 임시 구현뿐이다. JPA 어댑터에서 구현할 때 다음을 지킨다.
 *
 * <ul>
 *   <li>종료시각은 저장 컬럼이 아니라 시작시각 + 예상소요시간이다. 구간 조회는 {@code start_time >= from - 24시간 AND start_time < to}로
 *       시작시각 인덱스를 범위 스캔한 뒤 {@code start_time + expected_duration > from}으로 거른다(예상소요시간 상한이 24시간).
 *   <li>결과는 완전한 {@link Work} 애그리게잇이라 배정 이력·정정 기록·완료보고를 함께 읽는다. 목록마다 연관을 따로 읽는 N+1이 생기지 않게 일괄
 *       조회(fetch join·batch)로 읽거나, 목록 화면이 커지면 조회 전용 모델로 바꾼다.
 *   <li>경계(끝이 from과 같거나 시작이 to와 같으면 제외)와 검색 조건 해석을 실제 데이터로 확인하는 어댑터 테스트를 둔다.
 *   <li>대기함은 상한 없이 쌓일 수 있으므로 화면이 느려지면 쪽 나누기를 더한다.
 * </ul>
 */
public interface WorkQueryPort {

    /**
     * 현재 일정이 [from, to) 구간과 겹치는, 일정을 차지하거나 차지했던 작업(수락대기·수락됨·작업중·완료). 취소된 작업은 일정이 남아 있어도 담지 않는다.
     */
    List<Work> listScheduledBetween(OrganizationId organizationId, Instant from, Instant to);

    /** 대기함 작업(등록 상태). */
    List<Work> listBacklog(OrganizationId organizationId);

    /** 조건에 맞는 작업의 한 쪽과 전체 수. 이 메서드는 조건의 정렬 순서를 지킨다. */
    WorkSearchResult search(OrganizationId organizationId, WorkSearchCriteria criteria);
}

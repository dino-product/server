package com.orbit.schedule.application.port.out;

import java.util.List;
import java.util.Optional;

import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;

/**
 * Work 애그리게잇의 저장·조회 출력 포트.
 *
 * <ul>
 *   <li>{@link #findById}는 조직으로 거르지 않으므로 서비스가 조직을 비교하고, 다르면 존재 여부가 드러나지 않도록 WORK_NOT_FOUND로 처리한다.
 *   <li>{@link #save}는 새 작업이면 식별자를 채워 돌려주고, 기존 작업이면 같은 트랜잭션에서 {@link #findById}로 읽은 작업의 변경을 반영한다. 구현은 조회 시점의
 *       버전으로 동시 변경을 검출한다.
 *   <li>조회 결과는 영속 상태와 분리된 사본이다. {@link #save}를 호출하지 않은 변경은 트랜잭션이 커밋돼도 저장되지 않으며, 서비스는 이에 기대어 판정 결과에
 *       따라 저장을 생략한다(예: 확인하지 않은 일정 겹침).
 * </ul>
 */
public interface WorkRepository {

    Work save(Work work);

    Optional<Work> findById(WorkId workId);

    /**
     * 조직 안에서 기사에게 현재 배정된 활성 작업(수락대기·수락됨·작업중). 현재 배정(작업의 일정)의 기사로 찾으며 과거 배정 이력은 보지 않는다. 일정 겹침과 동시 수행
     * 판정의 후보이고 판정은 도메인 정책이 다시 거른다. 조직 간 겹침은 보지 않는다(BC-005). 순서는 보장하지 않으므로 표시 순서는 호출자가 정한다. 인자는 null이
     * 아니다.
     */
    List<Work> findActiveByTechnician(OrganizationId organizationId, MembershipId technicianId);
}

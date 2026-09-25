package com.orbit.schedule.application.port.out;

import java.util.List;
import java.util.Optional;

import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;

/**
 * Work 애그리게잇의 저장·조회 출력 포트. 조회는 모두 조직 범위라 다른 조직의 작업은 없는 것처럼 보인다.
 *
 * <ul>
 *   <li>{@link #save}는 새 작업이면 식별자를 채워 돌려주고, 기존 작업이면 같은 트랜잭션에서 읽은 작업의 변경을 반영한다. JPA 어댑터는 조회 시점의 버전으로,
 *       같은 작업을 동시에 바꾼 다른 트랜잭션을 검출한다(임시 메모리 구현은 검출하지 않고 덮어쓴다).
 *   <li>조회 결과는 영속 상태와 분리된 사본이다. {@link #save}를 호출하지 않은 변경은 트랜잭션이 커밋돼도 저장되지 않으며, 서비스는 이에 기대어 판정 결과에
 *       따라 저장을 생략한다(예: 확인하지 않은 일정 겹침).
 * </ul>
 *
 * <p>TODO(HM-234·HM-238): 버전 충돌을 어떤 예외·오류 코드로 알릴지 정해 JPA 어댑터에서 변환한다(변환하지 않으면 500). 사용자가 화면을 연 뒤 다른 사람이 바꾼
 * 작업을 덮어쓰지 않도록, 변경 명령에 클라이언트가 본 버전(expectedVersion)을 받아 비교할지도 이때 함께 정한다(지금은 마지막 요청이 이긴다).
 */
public interface WorkRepository {

    Work save(Work work);

    /** 조직의 작업을 찾는다. 없거나 다른 조직의 작업이면 비어 있다. 인자는 null이 아니다. */
    Optional<Work> findInOrganization(OrganizationId organizationId, WorkId workId);

    /**
     * 조직 안에서 기사에게 현재 배정된 활성 작업(수락대기·수락됨·작업중). 현재 배정(작업의 일정)의 기사로 찾으며 과거 배정 이력은 보지 않는다. 일정 겹침과 동시 수행
     * 판정의 후보이고 판정은 도메인 정책이 다시 거른다. 조직 간 겹침은 보지 않는다(BC-005). 순서는 보장하지 않으므로 표시 순서는 호출자가 정한다. 인자는 null이
     * 아니다.
     */
    List<Work> listActiveByTechnician(OrganizationId organizationId, MembershipId technicianId);
}

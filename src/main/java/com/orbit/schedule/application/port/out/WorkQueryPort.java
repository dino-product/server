package com.orbit.schedule.application.port.out;

import java.time.Instant;
import java.util.List;

import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.TechnicianId;
import com.orbit.schedule.domain.Work;

/**
 * 화면 조회용 작업 목록 출력 포트. 변경에 쓰는 {@link WorkRepository}와 달리 여러 작업을 조건으로 읽기만 한다. 조회는 모두 조직 범위이고, 결과는 영속 상태와
 * 분리된 사본이다. 순서를 정하는 검색 말고는 순서를 보장하지 않으므로 표시 순서는 호출자가 정한다. 인자는 null이 아니다.
 *
 * <p>구현이 지킬 조건:
 *
 * <ul>
 *   <li>종료시각은 저장 값이 아니라 시작시각 + 예상소요시간이다. 구간과 겹치는지는 끝이 from과 같거나 시작이 to와 같으면 겹치지 않는 반열림 구간으로 판정한다.
 *   <li>등록(대기함) 작업은 등록 때 미리 입력한 담당기사·시간을 가질 수 있지만 일정이 아니다. 일정으로 거르는 조회와 정렬은 대기함 작업의 미리 입력한 값을
 *       보지 않는다.
 *   <li>결과는 완전한 {@link Work} 애그리게잇(배정 이력·완료보고 포함)이고, 호출자가 저장하지 않은 변경은 반영되지 않는다.
 * </ul>
 *
 * <p>호출자 조건: 여러 번 읽어 한 화면을 만드는 조회(타임테이블의 칸·대기함, 검색의 목록·전체 수)는 같은 시점을 보도록 한 스냅샷 트랜잭션(REPEATABLE
 * READ)에서 부른다.
 *
 * <p>규모 한계: 대기함과 31일 구간 조회는 상한 없이 애그리게잇을 읽으므로, 조직 규모가 커지면 쪽 나누기나 화면에 필요한 값만 읽는 조회 전용 모델을 검토한다.
 */
public interface WorkQueryPort {

    /**
     * 현재 일정이 [from, to) 구간과 겹치는, 일정을 차지하거나 차지했던 작업(수락대기·수락됨·작업중·완료). 취소된 작업은 일정이 남아 있어도 담지 않는다.
     */
    List<Work> listScheduledBetween(OrganizationId organizationId, Instant from, Instant to);

    /** {@link #listScheduledBetween} 가운데 현재 일정의 담당이 그 기사인 작업. */
    List<Work> listScheduledForTechnician(
            OrganizationId organizationId, TechnicianId technicianId, Instant from, Instant to);

    /**
     * 그 기사가 한 번이라도 배정받은 작업 가운데, 그 기사의 배정(배정 이력의 일정) 중 하나라도 [from, to) 구간과 겹치는 작업. 지금 상태와 관계없이 담는다(다른
     * 기사로 바뀌었거나 거절·취소된 작업 포함). 돌려주는 작업은 반드시 이 조건을 만족하고, 겹치는 이력이 여럿이어도 작업마다 한 번만 담는다. 작업 이력·통계가
     * 쓴다.
     */
    List<Work> listAssignedToTechnician(
            OrganizationId organizationId, TechnicianId technicianId, Instant from, Instant to);

    /** 대기함 작업(등록 상태). 등록 때 미리 입력한 담당기사·시간이 있어도 대기함이므로 일정 컬럼으로 거르지 않는다. */
    List<Work> listBacklog(OrganizationId organizationId);

    /**
     * 조건에 맞는 작업의 한 쪽과 전체 수. 조건의 정렬 순서를 지킨다. 쪽이 끝을 넘으면 빈 목록과 실제 전체 수를 돌려준다. 목록과 전체 수는 따로 읽을 수
     * 있으므로 둘의 일관성은 호출 트랜잭션의 격리 수준에 따른다. 조건 해석(검색어 이스케이프, 지연 판정 시각, 정렬의 null 위치 등)은
     * {@link WorkSearchCriteria}를 따른다.
     */
    WorkSearchResult search(OrganizationId organizationId, WorkSearchCriteria criteria);

    /**
     * 조건과 정렬에 맞는 작업을 앞에서부터 최대 limit건 돌려준다. 조건의 쪽은 보지 않는다. 조건 해석은 {@link #search}와 같다. 한꺼번에 내려받는 출력이 쓰며,
     * 구현은 쪽을 건너뛰지 않고 정렬된 식별자를 한 번에 고른 뒤 작업을 나눠 읽는다. 여러 번 읽으므로 스냅샷 트랜잭션에서 부르고, 많은 작업을 읽는 동안 호출
     * 트랜잭션의 영속성 컨텍스트를 비울 수 있으므로 읽기 전용 트랜잭션에서만 부른다.
     */
    List<WorkSearchResult.SearchedWork> listMatching(
            OrganizationId organizationId, WorkSearchCriteria criteria, int limit);

    /** 조건에 맞는 작업 수. 조건의 정렬·쪽은 보지 않는다. 조건 해석은 {@link #search}와 같다. */
    long count(OrganizationId organizationId, WorkSearchCriteria criteria);
}

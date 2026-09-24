package com.orbit.schedule.application.port.out;

import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.OrganizationId;

/**
 * 같은 조직·같은 기사의 활성 작업을 읽어 판정하는 요청(일정 변경, 작업 시작의 동시 수행 판정 등)을 한 줄로 세우는 출력 포트. 일정 겹침·동시 수행은 서로 다른 작업
 * 사이의 판정이라 작업 버전 비교로는 막을 수 없으므로, 기사의 활성 작업을 읽기 전에 잠가 동시 요청이 앞선 요청의 저장 결과를 보고 판정하게 한다. 어느 유즈케이스가
 * 잠그는지는 schedule 지침이 원본이다.
 *
 * <ul>
 *   <li>진행 중인 트랜잭션 안에서, 저장소와 같은 연결로 잠근다. 잠금은 그 트랜잭션이 커밋·롤백될 때 풀리고, 트랜잭션 밖이면 거부한다.
 *   <li>잠근 뒤의 조회가 앞선 트랜잭션의 커밋을 볼 수 있어야 한다(READ COMMITTED). 그보다 높은 격리 수준에서는 스냅샷이 잠금 전에 고정돼 효과가 없다.
 *   <li>대기 한도를 넘기면 {@link TechnicianScheduleBusyException}으로 실패한다. 실패한 트랜잭션은 더 쓸 수 없으므로 호출자는 이 예외를 삼키지 않고
 *       롤백한다.
 *   <li>한 트랜잭션에서는 한 기사만 잠근다. 여러 기사를 잠가야 하면 교착을 피하도록 순서 규칙을 먼저 정한다. 같은 기사를 다시 잠그는 것은 괜찮다.
 *   <li>인자는 null이 아니다.
 * </ul>
 */
public interface LockTechnicianSchedulePort {

    void lock(OrganizationId organizationId, MembershipId technicianId);
}

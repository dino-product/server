package com.orbit.schedule.application.port.out;

import java.util.Optional;

import com.orbit.schedule.domain.Actor;
import com.orbit.schedule.domain.OrganizationId;

/**
 * 인증된 계정이 요청한 조직에서 어떤 요청자인지 찾는 출력 포트. 구현은 organization 모듈의 공개 계약으로 계정(accountId)의 그 조직 소속(관리자) 또는
 * 기사 계약을 조회해 schedule의 {@link Actor}로 변환한다. 비활성 소속·기사 계약은 보지 않는다. 한 사용자는 한 조직에서 둘 중 하나만 가지므로(비활성
 * 포함), 활성인 둘이 함께 조회되면 불변식 위반으로 보고 어느 쪽도 고르지 않고 실패한다. 활성인 것이 없으면 비어 있다. accountId는 인증 계층이 채우므로
 * null이 아니다.
 */
public interface LoadActorPort {

    Optional<Actor> findActiveActor(Long accountId, OrganizationId organizationId);
}

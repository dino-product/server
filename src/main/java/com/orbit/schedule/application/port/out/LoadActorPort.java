package com.orbit.schedule.application.port.out;

import java.util.Optional;

import com.orbit.schedule.domain.Actor;
import com.orbit.schedule.domain.OrganizationId;

/**
 * 인증된 계정이 요청한 조직에서 어떤 행위자인지 찾는 출력 포트. 구현은 organization 모듈의 공개 계약으로 계정(accountId)의 해당 조직 소속을 조회해
 * schedule의 Actor로 변환한다. 소속이 없거나 비활성이면 비어 있다.
 */
public interface LoadActorPort {

    Optional<Actor> findActiveActor(Long accountId, OrganizationId organizationId);
}

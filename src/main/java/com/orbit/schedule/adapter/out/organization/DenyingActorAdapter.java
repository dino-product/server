package com.orbit.schedule.adapter.out.organization;

import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.domain.Actor;
import com.orbit.schedule.domain.OrganizationId;

/**
 * 임시 LoadActorPort 구현. organization 모듈의 소속 조회 공개 계약이 생기면 그 계약을 호출하는 어댑터로 교체한 뒤 삭제한다. 그 전까지는 어떤 계정도 행위자로
 * 인정하지 않아(안전한 기본 동작) 모든 작업 변경이 권한 없음으로 끝난다. {@code local}·{@code test} 프로필에서만 등록한다.
 */
@Component
@Profile({"local & !prod", "test & !prod"})
class DenyingActorAdapter implements LoadActorPort {

    @Override
    public Optional<Actor> findActiveActor(Long accountId, OrganizationId organizationId) {
        return Optional.empty();
    }
}

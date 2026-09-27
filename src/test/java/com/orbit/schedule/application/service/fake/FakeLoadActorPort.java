package com.orbit.schedule.application.service.fake;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.domain.Actor;
import com.orbit.schedule.domain.ActorRole;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.OrganizationId;

/** 서비스 단위 테스트용 LoadActorPort. 스캔되지 않도록 애너테이션을 붙이지 않는다. 등록한 계정·조직 조합만 활성 행위자로 돌려준다. */
public class FakeLoadActorPort implements LoadActorPort {

    private final Map<String, Actor> actors = new HashMap<>();

    public Actor givenActor(Long accountId, OrganizationId organizationId, Long membershipId, ActorRole role) {
        Actor actor = new Actor(new MembershipId(membershipId), organizationId, role);
        actors.put(key(accountId, organizationId), actor);
        return actor;
    }

    @Override
    public Optional<Actor> findActiveActor(Long accountId, OrganizationId organizationId) {
        // 실제 포트도 식별자를 요구하므로, 서비스가 누락을 먼저 거르지 않으면 여기서 다른 메시지로 드러난다.
        Objects.requireNonNull(accountId, "fake port received null accountId");
        Objects.requireNonNull(organizationId, "fake port received null organizationId");
        return Optional.ofNullable(actors.get(key(accountId, organizationId)));
    }

    private static String key(Long accountId, OrganizationId organizationId) {
        return accountId + ":" + organizationId.value();
    }
}

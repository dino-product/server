package com.orbit.schedule.adapter.out.organization;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.orbit.organization.OrganizationMember;
import com.orbit.organization.OrganizationMemberLookup;
import com.orbit.organization.StaffMember;
import com.orbit.organization.TechnicianMember;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.domain.Actor;
import com.orbit.schedule.domain.ActorRole;
import com.orbit.schedule.domain.ManagerActor;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.TechnicianActor;
import com.orbit.schedule.domain.TechnicianId;

/**
 * organization의 활성 구성원 조회를 schedule의 요청자로 번역하는 ACL 어댑터. 직원 소속은 총관리자 표시에 따라 총관리자·직원 관리자로, 기사 계약은 같은 ID의
 * 기사로 옮긴다. 비활성 제외와 활성 둘의 불변식 위반은 organization 계약이 맡는다.
 */
@Component
class OrganizationActorAdapter implements LoadActorPort {

    private final OrganizationMemberLookup memberLookup;

    OrganizationActorAdapter(OrganizationMemberLookup memberLookup) {
        this.memberLookup = memberLookup;
    }

    @Override
    public Optional<Actor> findActiveActor(Long accountId, OrganizationId organizationId) {
        return memberLookup.findActiveMember(accountId, organizationId.value()).map(OrganizationActorAdapter::toActor);
    }

    private static Actor toActor(OrganizationMember member) {
        // 요청한 조직이 아니라 계약이 돌려준 조직을 그대로 옮겨, 다른 조직을 돌려주는 계약 오류를 서비스의 조직 일치 확인이 잡게 한다.
        OrganizationId organizationId = new OrganizationId(member.organizationId());
        return switch (member) {
            case StaffMember staff ->
                new ManagerActor(
                        new MembershipId(staff.membershipId()),
                        organizationId,
                        staff.owner() ? ActorRole.OWNER : ActorRole.STAFF);
            case TechnicianMember technician ->
                new TechnicianActor(new TechnicianId(technician.technicianId()), organizationId);
        };
    }
}

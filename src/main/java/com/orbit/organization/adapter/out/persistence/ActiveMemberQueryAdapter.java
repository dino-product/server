package com.orbit.organization.adapter.out.persistence;

import java.util.List;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Repository;

import com.orbit.organization.application.port.out.ActiveMember;
import com.orbit.organization.application.port.out.ActiveMemberQueryPort;
import com.orbit.organization.application.port.out.ActiveMembership;
import com.orbit.organization.application.port.out.ActiveTechnicianContract;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.MembershipId;
import com.orbit.organization.domain.MembershipStatus;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.TechnicianId;
import com.orbit.organization.domain.TechnicianStatus;

/** 직원 소속과 기사 계약 테이블을 UNION ALL 한 문장으로 읽어, READ COMMITTED에서도 두 테이블을 같은 시점으로 본다. */
@Repository
class ActiveMemberQueryAdapter implements ActiveMemberQueryPort {

    private static final String STAFF = "STAFF";

    private static final String ACTIVE_MEMBERS = """
            select 'STAFF' as kind, m.id, m.is_owner
            from company_memberships m
            where m.company_id = :organizationId and m.member_id = :accountId and m.status = :membershipStatus
            union all
            select 'TECHNICIAN' as kind, t.id, false
            from technician t
            where t.company_id = :organizationId and t.member_id = :accountId and t.status = :technicianStatus
            """;

    private final EntityManager entityManager;

    ActiveMemberQueryAdapter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public List<ActiveMember> findActiveMembers(OrganizationId organizationId, AccountId accountId) {
        List<?> rows = entityManager
                .createNativeQuery(ACTIVE_MEMBERS)
                .setParameter("organizationId", organizationId.value())
                .setParameter("accountId", accountId.value())
                .setParameter("membershipStatus", MembershipStatus.ACTIVE.name())
                .setParameter("technicianStatus", TechnicianStatus.ACTIVE.name())
                .getResultList();
        return rows.stream().map(row -> toActiveMember((Object[]) row)).toList();
    }

    private static ActiveMember toActiveMember(Object[] row) {
        long id = ((Number) row[1]).longValue();
        if (STAFF.equals(row[0])) {
            return new ActiveMembership(new MembershipId(id), (Boolean) row[2]);
        }
        return new ActiveTechnicianContract(new TechnicianId(id));
    }
}

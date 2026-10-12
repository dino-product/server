package com.orbit.organization.adapter.out.persistence;

import java.time.Instant;
import java.util.List;

import jakarta.persistence.EntityManager;

import org.hibernate.query.NativeQuery;
import org.hibernate.type.StandardBasicTypes;
import org.springframework.stereotype.Repository;

import com.orbit.organization.application.port.out.AccountMembership;
import com.orbit.organization.application.port.out.AccountMembershipQueryPort;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.MemberRole;
import com.orbit.organization.domain.MembershipStatus;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.TechnicianStatus;

/**
 * 직원 소속과 기사 계약 테이블을 UNION ALL 한 문장으로 읽어, READ COMMITTED에서도 역할 변경 전후의 두 행을 같은 시점으로 본다. 기사 계약은 참여 시각 컬럼이 없어
 * 생성 시각을 참여 시각으로 쓴다.
 */
@Repository
class AccountMembershipQueryAdapter implements AccountMembershipQueryPort {

    private static final String ACCOUNT_MEMBERSHIPS = """
            select m.company_id as organization_id, c.name as organization_name,
                   case when m.is_owner then 'OWNER' else 'STAFF' end as role,
                   m.status = :membershipActive as active, m.joined_at, m.status_changed_at
            from company_memberships m
            join companies c on c.id = m.company_id
            where m.member_id = :accountId
            union all
            select t.company_id, c.name, 'TECHNICIAN', t.status = :technicianActive, t.created_at, t.status_changed_at
            from technician t
            join companies c on c.id = t.company_id
            where t.member_id = :accountId
            """;

    private final EntityManager entityManager;

    AccountMembershipQueryAdapter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<AccountMembership> listMemberships(AccountId accountId) {
        List<Object[]> rows = entityManager
                .createNativeQuery(ACCOUNT_MEMBERSHIPS)
                .unwrap(NativeQuery.class)
                .addScalar("organization_id", StandardBasicTypes.LONG)
                .addScalar("organization_name", StandardBasicTypes.STRING)
                .addScalar("role", StandardBasicTypes.STRING)
                .addScalar("active", StandardBasicTypes.BOOLEAN)
                .addScalar("joined_at", StandardBasicTypes.INSTANT)
                .addScalar("status_changed_at", StandardBasicTypes.INSTANT)
                .setParameter("accountId", accountId.value())
                .setParameter("membershipActive", MembershipStatus.ACTIVE.name())
                .setParameter("technicianActive", TechnicianStatus.ACTIVE.name())
                .getResultList();
        return rows.stream()
                .map(AccountMembershipQueryAdapter::toAccountMembership)
                .toList();
    }

    private static AccountMembership toAccountMembership(Object[] row) {
        return new AccountMembership(
                new OrganizationId((Long) row[0]),
                (String) row[1],
                MemberRole.valueOf((String) row[2]),
                (Boolean) row[3],
                (Instant) row[4],
                (Instant) row[5]);
    }
}

package com.orbit.organization.adapter.out.persistence;

import java.time.Instant;
import java.util.List;

import jakarta.persistence.EntityManager;

import org.hibernate.query.NativeQuery;
import org.hibernate.type.StandardBasicTypes;
import org.springframework.stereotype.Repository;

import com.orbit.organization.application.port.out.ParticipantQueryPort;
import com.orbit.organization.application.port.out.ParticipantRow;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.MemberRole;
import com.orbit.organization.domain.MembershipStatus;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.TechnicianStatus;

/**
 * 직원 소속과 기사 계약 테이블을 UNION ALL 한 문장으로 읽어, READ COMMITTED에서도 역할 변경 전후의 두 행을 같은 시점으로 본다. 유형 삭제는 지정을 같은
 * 트랜잭션에서 해제하므로 지정된 유형은 삭제되지 않은 유형이다.
 */
@Repository
class ParticipantQueryAdapter implements ParticipantQueryPort {

    private static final String PARTICIPANTS = """
            select case when m.is_owner then 'OWNER' else 'STAFF' end as role, m.id as member_id,
                   m.member_id as account_id, m.status = :membershipActive as active,
                   c.id as category_id, c.name as category_name, c.color as category_color,
                   m.joined_at, m.status_changed_at
            from company_memberships m
            left join company_membership_category c on c.id = m.company_membership_category_id
            where m.company_id = :organizationId
            union all
            select 'TECHNICIAN', t.id, t.member_id, t.status = :technicianActive,
                   c.id, c.name, c.color, t.joined_at, t.status_changed_at
            from technician t
            left join company_technician_category c on c.id = t.company_technician_category_id
            where t.company_id = :organizationId
            """;

    private final EntityManager entityManager;

    ParticipantQueryAdapter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ParticipantRow> listParticipants(OrganizationId organizationId) {
        List<Object[]> rows = entityManager
                .createNativeQuery(PARTICIPANTS)
                .unwrap(NativeQuery.class)
                .addScalar("role", StandardBasicTypes.STRING)
                .addScalar("member_id", StandardBasicTypes.LONG)
                .addScalar("account_id", StandardBasicTypes.LONG)
                .addScalar("active", StandardBasicTypes.BOOLEAN)
                .addScalar("category_id", StandardBasicTypes.LONG)
                .addScalar("category_name", StandardBasicTypes.STRING)
                .addScalar("category_color", StandardBasicTypes.STRING)
                .addScalar("joined_at", StandardBasicTypes.INSTANT)
                .addScalar("status_changed_at", StandardBasicTypes.INSTANT)
                .setParameter("organizationId", organizationId.value())
                .setParameter("membershipActive", MembershipStatus.ACTIVE.name())
                .setParameter("technicianActive", TechnicianStatus.ACTIVE.name())
                .getResultList();
        return rows.stream().map(ParticipantQueryAdapter::toRow).toList();
    }

    private static ParticipantRow toRow(Object[] row) {
        return new ParticipantRow(
                MemberRole.valueOf((String) row[0]),
                (Long) row[1],
                new AccountId((Long) row[2]),
                (Boolean) row[3],
                (Long) row[4],
                (String) row[5],
                (String) row[6],
                (Instant) row[7],
                (Instant) row[8]);
    }
}

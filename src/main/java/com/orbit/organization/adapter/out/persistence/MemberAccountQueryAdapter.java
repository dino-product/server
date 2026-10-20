package com.orbit.organization.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManager;

import org.hibernate.query.NativeQuery;
import org.hibernate.type.StandardBasicTypes;
import org.springframework.stereotype.Repository;

import com.orbit.organization.application.port.out.MemberAccountQueryPort;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.MembershipId;
import com.orbit.organization.domain.MembershipStatus;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.TechnicianId;
import com.orbit.organization.domain.TechnicianStatus;

/** 엔티티가 아닌 스칼라 값으로 읽어, 호출 트랜잭션의 영속성 컨텍스트에 소속·기사 계약 엔티티가 남아 있어도 그 뒤 DB에 쓰인 상태를 본다. */
@Repository
class MemberAccountQueryAdapter implements MemberAccountQueryPort {

    private static final String ACTIVE_STAFF_ACCOUNT = """
            select m.member_id
            from company_memberships m
            where m.id = :id and m.company_id = :organizationId and m.status = :active
            """;

    private static final String ACTIVE_TECHNICIAN_ACCOUNT = """
            select t.member_id
            from technician t
            where t.id = :id and t.company_id = :organizationId and t.status = :active
            """;

    private final EntityManager entityManager;

    MemberAccountQueryAdapter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Optional<AccountId> findActiveStaffAccountId(OrganizationId organizationId, MembershipId membershipId) {
        return findAccountId(
                ACTIVE_STAFF_ACCOUNT, membershipId.value(), organizationId, MembershipStatus.ACTIVE.name());
    }

    @Override
    public Optional<AccountId> findActiveTechnicianAccountId(OrganizationId organizationId, TechnicianId technicianId) {
        return findAccountId(
                ACTIVE_TECHNICIAN_ACCOUNT, technicianId.value(), organizationId, TechnicianStatus.ACTIVE.name());
    }

    @SuppressWarnings("unchecked")
    private Optional<AccountId> findAccountId(String sql, Long id, OrganizationId organizationId, String activeStatus) {
        List<Long> accountIds = entityManager
                .createNativeQuery(sql)
                .unwrap(NativeQuery.class)
                .addScalar("member_id", StandardBasicTypes.LONG)
                .setParameter("id", id)
                .setParameter("organizationId", organizationId.value())
                .setParameter("active", activeStatus)
                .getResultList();
        return accountIds.stream().findFirst().map(AccountId::new);
    }
}

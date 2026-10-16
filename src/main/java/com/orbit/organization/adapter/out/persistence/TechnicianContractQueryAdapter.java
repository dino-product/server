package com.orbit.organization.adapter.out.persistence;

import java.util.List;

import jakarta.persistence.EntityManager;

import org.hibernate.query.NativeQuery;
import org.hibernate.type.StandardBasicTypes;
import org.springframework.stereotype.Repository;

import com.orbit.organization.application.port.out.AccountTechnicianContract;
import com.orbit.organization.application.port.out.TechnicianContractQueryPort;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.TechnicianId;
import com.orbit.organization.domain.TechnicianStatus;

/**
 * 엔티티가 아닌 스칼라 값으로 읽어, 호출 트랜잭션의 영속성 컨텍스트에 기사 계약 엔티티가 남아 있어도 그 뒤 DB에 쓰인 상태를 본다. 계정의 계약 목록은 schedule의
 * 탈퇴 뒤처리가 반환할 계약을 정할 때, 활성 확인은 schedule이 배정할 기사를 잠근 뒤 쓰므로 영속성 컨텍스트에 남은 오래된 상태를 돌려주면 안 된다.
 */
@Repository
class TechnicianContractQueryAdapter implements TechnicianContractQueryPort {

    private static final String ACCOUNT_TECHNICIAN_CONTRACTS = """
            select t.id, t.company_id, t.status = :active as active
            from technician t
            where t.member_id = :accountId
            """;

    private static final String ACTIVE_CONTRACT_EXISTS = """
            select exists (
                select 1 from technician t
                where t.id = :technicianId and t.company_id = :organizationId and t.status = :active
            ) as active
            """;

    private final EntityManager entityManager;

    TechnicianContractQueryAdapter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<AccountTechnicianContract> listByAccount(AccountId accountId) {
        List<Object[]> rows = entityManager
                .createNativeQuery(ACCOUNT_TECHNICIAN_CONTRACTS)
                .unwrap(NativeQuery.class)
                .addScalar("id", StandardBasicTypes.LONG)
                .addScalar("company_id", StandardBasicTypes.LONG)
                .addScalar("active", StandardBasicTypes.BOOLEAN)
                .setParameter("accountId", accountId.value())
                .setParameter("active", TechnicianStatus.ACTIVE.name())
                .getResultList();
        return rows.stream().map(TechnicianContractQueryAdapter::toContract).toList();
    }

    @Override
    public boolean existsActive(OrganizationId organizationId, TechnicianId technicianId) {
        return (Boolean) entityManager
                .createNativeQuery(ACTIVE_CONTRACT_EXISTS)
                .unwrap(NativeQuery.class)
                .addScalar("active", StandardBasicTypes.BOOLEAN)
                .setParameter("technicianId", technicianId.value())
                .setParameter("organizationId", organizationId.value())
                .setParameter("active", TechnicianStatus.ACTIVE.name())
                .getSingleResult();
    }

    private static AccountTechnicianContract toContract(Object[] row) {
        return new AccountTechnicianContract(
                new TechnicianId((Long) row[0]), new OrganizationId((Long) row[1]), (Boolean) row[2]);
    }
}

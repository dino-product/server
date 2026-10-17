package com.orbit.organization.adapter.out.persistence;

import java.util.List;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Repository;

import com.orbit.organization.application.port.out.AccountParticipationRequest;
import com.orbit.organization.application.port.out.ParticipationRequestQueryPort;
import com.orbit.organization.domain.AccountId;

/** 요청과 발주사명을 한 문장으로 읽는다. 발주사명은 요청 당시가 아니라 지금 이름이다. */
@Repository
class ParticipationRequestQueryAdapter implements ParticipationRequestQueryPort {

    private static final String ACCOUNT_REQUESTS = """
            select r, o.name
            from ParticipationRequestJpaEntity r
            join OrganizationJpaEntity o on o.id = r.organizationId
            where r.accountId = :accountId
            order by r.requestedAt, r.id
            """;

    private final EntityManager entityManager;

    ParticipationRequestQueryAdapter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public List<AccountParticipationRequest> listByAccount(AccountId accountId) {
        return entityManager
                .createQuery(ACCOUNT_REQUESTS, Object[].class)
                .setParameter("accountId", accountId.value())
                .getResultList()
                .stream()
                .map(row -> new AccountParticipationRequest(
                        ((ParticipationRequestJpaEntity) row[0]).toDomain(), (String) row[1]))
                .toList();
    }
}

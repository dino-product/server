package com.orbit.organization.adapter.out.persistence;

import java.util.List;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Repository;

import com.orbit.organization.application.port.out.AccountParticipationRequest;
import com.orbit.organization.application.port.out.ParticipationRequestQueryPort;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.ParticipationRequest;
import com.orbit.organization.domain.ParticipationRequestStatus;

/** 참여 요청 목록 조회. 계정의 요청은 발주사명과 한 문장으로 읽으며, 발주사명은 요청 당시가 아니라 지금 이름이다. */
@Repository
class ParticipationRequestQueryAdapter implements ParticipationRequestQueryPort {

    private static final String ACCOUNT_REQUESTS = """
            select r, o.name
            from ParticipationRequestJpaEntity r
            join OrganizationJpaEntity o on o.id = r.organizationId
            where r.accountId = :accountId
            order by r.requestedAt, r.id
            """;

    private static final String PENDING_REQUESTS = """
            select r
            from ParticipationRequestJpaEntity r
            where r.organizationId = :organizationId and r.status = :status
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

    @Override
    public List<ParticipationRequest> listPending(OrganizationId organizationId) {
        return entityManager
                .createQuery(PENDING_REQUESTS, ParticipationRequestJpaEntity.class)
                .setParameter("organizationId", organizationId.value())
                .setParameter("status", ParticipationRequestStatus.PENDING)
                .getResultStream()
                .map(ParticipationRequestJpaEntity::toDomain)
                .toList();
    }
}

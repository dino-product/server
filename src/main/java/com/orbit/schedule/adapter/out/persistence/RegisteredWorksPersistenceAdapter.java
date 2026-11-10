package com.orbit.schedule.adapter.out.persistence;

import java.util.Set;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Repository;

import com.orbit.schedule.application.port.out.RegisteredWorksPort;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.WorkStatus;
import com.querydsl.jpa.impl.JPAQueryFactory;

/** 조직 소속이 등록한 작업의 존재 확인. 행을 잠그지 않는 한 문장의 조회다. */
@Repository
class RegisteredWorksPersistenceAdapter implements RegisteredWorksPort {

    private static final QWorkJpaEntity WORK = QWorkJpaEntity.workJpaEntity;

    private final JPAQueryFactory queryFactory;

    RegisteredWorksPersistenceAdapter(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Override
    public boolean existsRegisteredBy(
            OrganizationId organizationId, MembershipId registrarId, Set<WorkStatus> statuses) {
        return queryFactory
                        .selectOne()
                        .from(WORK)
                        .where(
                                WORK.companyId.eq(organizationId.value()),
                                WORK.createdBy.eq(registrarId.value()),
                                WORK.status.in(statuses))
                        .fetchFirst()
                != null;
    }
}

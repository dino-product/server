package com.orbit.organization.adapter.out.persistence;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Repository;

import com.orbit.organization.application.port.out.OrganizationIdentityPort;
import com.orbit.organization.domain.MembershipId;
import com.orbit.organization.domain.OrganizationId;

@Repository
class OrganizationSequenceAdapter implements OrganizationIdentityPort {
    private final EntityManager entityManager;

    OrganizationSequenceAdapter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public OrganizationId nextOrganizationId() {
        return new OrganizationId(nextValue("select nextval('organization_id_seq')"));
    }

    @Override
    public MembershipId nextMembershipId() {
        return new MembershipId(nextValue("select nextval('membership_id_seq')"));
    }

    private long nextValue(String sql) {
        return ((Number) entityManager.createNativeQuery(sql).getSingleResult()).longValue();
    }
}

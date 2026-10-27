package com.orbit.organization.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.orbit.organization.application.port.out.ParticipantRow;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.MemberRole;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.support.PersistenceAdapterTestSupport;

/** 역할 변경 유즈케이스가 아직 없어 한 계정의 직원 소속·기사 계약 두 행은 SQL로 넣는다. */
@DisplayName("발주사의 직원 소속·기사 계약 한 문장 조회")
class ParticipantQueryAdapterTest extends PersistenceAdapterTestSupport {

    private static final Instant JOINED_AT = Instant.parse("2026-10-01T00:00:00.123456Z");
    private static final Instant CHANGED_AT = Instant.parse("2026-10-05T00:00:00.654321Z");
    private static final AccountId OWNER = new AccountId(7L);
    private static final AccountId STAFF = new AccountId(8L);
    private static final AccountId TECHNICIAN = new AccountId(9L);

    @Autowired
    private EntityManager entityManager;

    private ParticipantQueryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ParticipantQueryAdapter(entityManager);
    }

    @Test
    @DisplayName("구성원이 없는 발주사는 빈 목록이다")
    void returnsEmptyListWithoutParticipants() {
        assertThat(adapter.listParticipants(insertOrganization("AAAAAA"))).isEmpty();
    }

    @Test
    @DisplayName("총관리자·직원 소속과 기사 계약을 상태·세부 유형·시각과 함께 읽는다")
    void readsMembershipsAndTechniciansWithCategory() {
        OrganizationId organizationId = insertOrganization("AAAAAA");
        long staffCategory = insertCategory("company_membership_category", organizationId, "사무", "#00FF00");
        long technicianCategory = insertCategory("company_technician_category", organizationId, "에어컨", "#FF0000");
        long owner = insertMembership(organizationId, OWNER, true, "ACTIVE", null);
        long staff = insertMembership(organizationId, STAFF, false, "DEACTIVATED", staffCategory);
        long technician = insertTechnician(organizationId, TECHNICIAN, "ACTIVE", technicianCategory);

        assertThat(adapter.listParticipants(organizationId))
                .containsExactlyInAnyOrder(
                        new ParticipantRow(
                                MemberRole.OWNER, owner, OWNER, true, null, null, null, JOINED_AT, CHANGED_AT),
                        new ParticipantRow(
                                MemberRole.STAFF,
                                staff,
                                STAFF,
                                false,
                                staffCategory,
                                "사무",
                                "#00FF00",
                                JOINED_AT,
                                CHANGED_AT),
                        new ParticipantRow(
                                MemberRole.TECHNICIAN,
                                technician,
                                TECHNICIAN,
                                true,
                                technicianCategory,
                                "에어컨",
                                "#FF0000",
                                JOINED_AT,
                                CHANGED_AT));
    }

    @Test
    @DisplayName("한 계정의 직원 소속과 기사 계약을 둘 다 읽어 호출자가 한 줄을 고르게 한다")
    void readsBothRowsOfSameAccount() {
        OrganizationId organizationId = insertOrganization("AAAAAA");
        insertMembership(organizationId, STAFF, false, "DEACTIVATED", null);
        insertTechnician(organizationId, STAFF, "ACTIVE", null);

        assertThat(adapter.listParticipants(organizationId))
                .extracting(ParticipantRow::role)
                .containsExactlyInAnyOrder(MemberRole.STAFF, MemberRole.TECHNICIAN);
    }

    @Test
    @DisplayName("다른 발주사의 소속과 기사 계약은 읽지 않는다")
    void ignoresOtherOrganizations() {
        OrganizationId organizationId = insertOrganization("AAAAAA");
        OrganizationId other = insertOrganization("BBBBBB");
        insertMembership(other, OWNER, true, "ACTIVE", null);
        insertTechnician(other, TECHNICIAN, "ACTIVE", null);

        assertThat(adapter.listParticipants(organizationId)).isEmpty();
    }

    private OrganizationId insertOrganization(String code) {
        entityManager
                .createNativeQuery("""
                        insert into companies (code, name, industry_code, created_at, updated_at)
                        values (?, '오르빗 설비', 'HVAC', ?, ?)
                        """)
                .setParameter(1, code)
                .setParameter(2, JOINED_AT)
                .setParameter(3, JOINED_AT)
                .executeUpdate();
        return new OrganizationId(lastId("companies"));
    }

    private long insertCategory(String table, OrganizationId organizationId, String name, String color) {
        entityManager
                .createNativeQuery("insert into " + table
                        + " (company_id, name, color, is_deleted, created_at, updated_at)"
                        + " values (?, ?, ?, false, current_timestamp, current_timestamp)")
                .setParameter(1, organizationId.value())
                .setParameter(2, name)
                .setParameter(3, color)
                .executeUpdate();
        return lastId(table);
    }

    private long insertMembership(
            OrganizationId organizationId, AccountId accountId, boolean owner, String status, Long categoryId) {
        entityManager
                .createNativeQuery("""
                        insert into company_memberships
                            (company_id, member_id, is_owner, status, company_membership_category_id,
                             joined_at, status_changed_at, created_at, updated_at)
                        values (?, ?, ?, ?, ?, ?, ?, current_timestamp, current_timestamp)
                        """)
                .setParameter(1, organizationId.value())
                .setParameter(2, accountId.value())
                .setParameter(3, owner)
                .setParameter(4, status)
                .setParameter(5, categoryId)
                .setParameter(6, JOINED_AT)
                .setParameter(7, CHANGED_AT)
                .executeUpdate();
        return lastId("company_memberships");
    }

    private long insertTechnician(OrganizationId organizationId, AccountId accountId, String status, Long categoryId) {
        entityManager
                .createNativeQuery("""
                        insert into technician
                            (company_id, member_id, status, company_technician_category_id,
                             joined_at, status_changed_at, created_at, updated_at)
                        values (?, ?, ?, ?, ?, ?, current_timestamp, current_timestamp)
                        """)
                .setParameter(1, organizationId.value())
                .setParameter(2, accountId.value())
                .setParameter(3, status)
                .setParameter(4, categoryId)
                .setParameter(5, JOINED_AT)
                .setParameter(6, CHANGED_AT)
                .executeUpdate();
        return lastId("technician");
    }

    private long lastId(String table) {
        return ((Number) entityManager
                        .createNativeQuery("select max(id) from " + table)
                        .getSingleResult())
                .longValue();
    }
}

package com.orbit.organization.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.MembershipId;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.TechnicianId;
import com.orbit.support.TestcontainersConfiguration;

/** 비활성 소속·기사 계약을 만드는 유즈케이스가 아직 없어 소속·기사 계약 행은 SQL로 넣는다. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("활성 직원 소속·기사 계약의 계정 조회(PostgreSQL)")
class MemberAccountQueryAdapterTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-01T00:00:00.123456Z");
    private static final Instant CHANGED_AT = Instant.parse("2026-10-05T00:00:00.654321Z");
    private static final OrganizationId ORGANIZATION_ID = new OrganizationId(100L);
    private static final OrganizationId OTHER_ORGANIZATION_ID = new OrganizationId(200L);
    private static final AccountId ACCOUNT_ID = new AccountId(7L);
    private static final AccountId OTHER_ACCOUNT_ID = new AccountId(8L);

    @Autowired
    private EntityManager entityManager;

    private MemberAccountQueryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new MemberAccountQueryAdapter(entityManager);
    }

    @Test
    @DisplayName("이 발주사의 활성 직원 소속이면 그 계정을 찾는다")
    void findsAccountOfActiveStaffMembership() {
        long id = insertMembership(ORGANIZATION_ID, ACCOUNT_ID, false, "ACTIVE");

        assertThat(adapter.findActiveStaffAccountId(ORGANIZATION_ID, new MembershipId(id)))
                .contains(ACCOUNT_ID);
    }

    @Test
    @DisplayName("활성 총관리자 소속도 그 계정을 찾는다")
    void findsAccountOfActiveOwnerMembership() {
        long id = insertMembership(ORGANIZATION_ID, ACCOUNT_ID, true, "ACTIVE");

        assertThat(adapter.findActiveStaffAccountId(ORGANIZATION_ID, new MembershipId(id)))
                .contains(ACCOUNT_ID);
    }

    @Test
    @DisplayName("다른 발주사의 소속, 비활성 소속, 없는 소속은 찾지 않는다")
    void ignoresMembershipOfOtherOrganizationInactiveOrMissing() {
        long otherOrganization = insertMembership(OTHER_ORGANIZATION_ID, ACCOUNT_ID, false, "ACTIVE");
        long deactivated = insertMembership(ORGANIZATION_ID, OTHER_ACCOUNT_ID, false, "DEACTIVATED");

        assertThat(adapter.findActiveStaffAccountId(ORGANIZATION_ID, new MembershipId(otherOrganization)))
                .isEmpty();
        assertThat(adapter.findActiveStaffAccountId(ORGANIZATION_ID, new MembershipId(deactivated)))
                .isEmpty();
        assertThat(adapter.findActiveStaffAccountId(ORGANIZATION_ID, new MembershipId(deactivated + 1000)))
                .isEmpty();
    }

    @Test
    @DisplayName("이미 읽어 둔 소속 엔티티가 있어도 그 뒤 DB에 쓰인 상태를 읽는다")
    void staffLookupReadsDatabaseStateInsteadOfLoadedEntity() {
        long id = insertMembership(ORGANIZATION_ID, ACCOUNT_ID, false, "ACTIVE");
        entityManager.find(MembershipJpaEntity.class, id);
        deactivate("company_memberships", id);

        assertThat(adapter.findActiveStaffAccountId(ORGANIZATION_ID, new MembershipId(id)))
                .isEmpty();
    }

    @Test
    @DisplayName("이 발주사의 활성 기사 계약이면 그 계정을 찾는다")
    void findsAccountOfActiveTechnicianContract() {
        long id = insertTechnician(ORGANIZATION_ID, ACCOUNT_ID, "ACTIVE");

        assertThat(adapter.findActiveTechnicianAccountId(ORGANIZATION_ID, new TechnicianId(id)))
                .contains(ACCOUNT_ID);
    }

    @Test
    @DisplayName("다른 발주사의 기사 계약, 비활성 계약, 없는 계약은 찾지 않는다")
    void ignoresTechnicianContractOfOtherOrganizationInactiveOrMissing() {
        long otherOrganization = insertTechnician(OTHER_ORGANIZATION_ID, ACCOUNT_ID, "ACTIVE");
        long deactivated = insertTechnician(ORGANIZATION_ID, OTHER_ACCOUNT_ID, "DEACTIVATED");

        assertThat(adapter.findActiveTechnicianAccountId(ORGANIZATION_ID, new TechnicianId(otherOrganization)))
                .isEmpty();
        assertThat(adapter.findActiveTechnicianAccountId(ORGANIZATION_ID, new TechnicianId(deactivated)))
                .isEmpty();
        assertThat(adapter.findActiveTechnicianAccountId(ORGANIZATION_ID, new TechnicianId(deactivated + 1000)))
                .isEmpty();
    }

    @Test
    @DisplayName("이미 읽어 둔 기사 계약 엔티티가 있어도 그 뒤 DB에 쓰인 상태를 읽는다")
    void technicianLookupReadsDatabaseStateInsteadOfLoadedEntity() {
        long id = insertTechnician(ORGANIZATION_ID, ACCOUNT_ID, "ACTIVE");
        entityManager.find(TechnicianJpaEntity.class, id);
        deactivate("technician", id);

        assertThat(adapter.findActiveTechnicianAccountId(ORGANIZATION_ID, new TechnicianId(id)))
                .isEmpty();
    }

    private long insertMembership(OrganizationId organizationId, AccountId accountId, boolean owner, String status) {
        entityManager
                .createNativeQuery("""
                        insert into company_memberships
                            (company_id, member_id, is_owner, status,
                             joined_at, status_changed_at, created_at, updated_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .setParameter(1, organizationId.value())
                .setParameter(2, accountId.value())
                .setParameter(3, owner)
                .setParameter(4, status)
                .setParameter(5, CREATED_AT)
                .setParameter(6, CHANGED_AT)
                .setParameter(7, CREATED_AT)
                .setParameter(8, CHANGED_AT)
                .executeUpdate();
        return lastId("company_memberships");
    }

    private long insertTechnician(OrganizationId organizationId, AccountId accountId, String status) {
        entityManager
                .createNativeQuery("""
                        insert into technician
                            (company_id, member_id, status, status_changed_at, created_at, updated_at)
                        values (?, ?, ?, ?, ?, ?)
                        """)
                .setParameter(1, organizationId.value())
                .setParameter(2, accountId.value())
                .setParameter(3, status)
                .setParameter(4, CHANGED_AT)
                .setParameter(5, CREATED_AT)
                .setParameter(6, CHANGED_AT)
                .executeUpdate();
        return lastId("technician");
    }

    private void deactivate(String table, long id) {
        entityManager
                .createNativeQuery("update " + table + " set status = 'DEACTIVATED' where id = ?")
                .setParameter(1, id)
                .executeUpdate();
    }

    private long lastId(String table) {
        return ((Number) entityManager
                        .createNativeQuery("select max(id) from " + table)
                        .getSingleResult())
                .longValue();
    }
}

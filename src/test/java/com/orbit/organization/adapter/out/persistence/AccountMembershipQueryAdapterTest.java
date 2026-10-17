package com.orbit.organization.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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

import com.orbit.organization.application.port.out.AccountMembership;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.MemberRole;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.support.TestcontainersConfiguration;

/** 역할 변경·참여 요청 승인 유즈케이스가 아직 없어 발주사·소속·기사 계약 행은 SQL로 넣는다. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("계정의 직원 소속·기사 계약 한 문장 조회")
class AccountMembershipQueryAdapterTest {

    private static final Instant JOINED_AT = Instant.parse("2026-10-01T00:00:00.123456Z");
    private static final Instant CHANGED_AT = Instant.parse("2026-10-05T00:00:00.654321Z");
    private static final AccountId ACCOUNT_ID = new AccountId(7L);
    private static final AccountId OTHER_ACCOUNT_ID = new AccountId(8L);

    @Autowired
    private EntityManager entityManager;

    private AccountMembershipQueryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new AccountMembershipQueryAdapter(entityManager);
    }

    @Test
    @DisplayName("소속이 없으면 빈 목록이다")
    void returnsEmptyListWithoutMembership() {
        assertThat(adapter.listMemberships(ACCOUNT_ID)).isEmpty();
    }

    @Test
    @DisplayName("총관리자·직원 소속과 기사 계약을 발주사명·상태·시각과 함께 읽는다")
    void readsMembershipsAndTechnicianContractsWithOrganizationName() {
        OrganizationId owned = insertOrganization("AAAAAA", "총관리 발주사");
        OrganizationId staffed = insertOrganization("BBBBBB", "직원 발주사");
        OrganizationId contracted = insertOrganization("CCCCCC", "기사 발주사");
        insertMembership(owned, ACCOUNT_ID, true, "ACTIVE");
        insertMembership(staffed, ACCOUNT_ID, false, "DEACTIVATED");
        insertTechnician(contracted, ACCOUNT_ID, "ACTIVE");

        assertThat(adapter.listMemberships(ACCOUNT_ID))
                .containsExactlyInAnyOrder(
                        new AccountMembership(owned, "총관리 발주사", MemberRole.OWNER, true, JOINED_AT, CHANGED_AT),
                        new AccountMembership(staffed, "직원 발주사", MemberRole.STAFF, false, JOINED_AT, CHANGED_AT),
                        new AccountMembership(
                                contracted, "기사 발주사", MemberRole.TECHNICIAN, true, JOINED_AT, CHANGED_AT));
    }

    @Test
    @DisplayName("한 발주사의 직원 소속과 기사 계약을 둘 다 읽어 호출자가 한 줄을 고르게 한다")
    void readsBothRowsOfSameOrganization() {
        OrganizationId organizationId = insertOrganization("AAAAAA", "오르빗 설비");
        insertMembership(organizationId, ACCOUNT_ID, false, "DEACTIVATED");
        insertTechnician(organizationId, ACCOUNT_ID, "ACTIVE");

        assertThat(adapter.listMemberships(ACCOUNT_ID))
                .extracting(AccountMembership::role, AccountMembership::active)
                .containsExactlyInAnyOrder(tuple(MemberRole.STAFF, false), tuple(MemberRole.TECHNICIAN, true));
    }

    @Test
    @DisplayName("다른 계정의 소속과 기사 계약은 읽지 않는다")
    void ignoresOtherAccounts() {
        OrganizationId organizationId = insertOrganization("AAAAAA", "오르빗 설비");
        insertMembership(organizationId, OTHER_ACCOUNT_ID, true, "ACTIVE");
        insertTechnician(insertOrganization("BBBBBB", "다른 발주사"), OTHER_ACCOUNT_ID, "ACTIVE");

        assertThat(adapter.listMemberships(ACCOUNT_ID)).isEmpty();
    }

    private OrganizationId insertOrganization(String code, String name) {
        entityManager
                .createNativeQuery("""
                        insert into companies (code, name, industry_code, created_at, updated_at)
                        values (?, ?, 'HVAC', ?, ?)
                        """)
                .setParameter(1, code)
                .setParameter(2, name)
                .setParameter(3, JOINED_AT)
                .setParameter(4, JOINED_AT)
                .executeUpdate();
        return new OrganizationId(lastId("companies"));
    }

    private void insertMembership(OrganizationId organizationId, AccountId accountId, boolean owner, String status) {
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
                .setParameter(5, JOINED_AT)
                .setParameter(6, CHANGED_AT)
                .setParameter(7, JOINED_AT)
                .setParameter(8, CHANGED_AT)
                .executeUpdate();
    }

    private void insertTechnician(OrganizationId organizationId, AccountId accountId, String status) {
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
                .setParameter(5, JOINED_AT)
                .setParameter(6, CHANGED_AT)
                .executeUpdate();
    }

    private long lastId(String table) {
        return ((Number) entityManager
                        .createNativeQuery("select max(id) from " + table)
                        .getSingleResult())
                .longValue();
    }
}

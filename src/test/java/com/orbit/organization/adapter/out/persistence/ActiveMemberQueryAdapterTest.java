package com.orbit.organization.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.orbit.organization.application.port.out.ActiveMembership;
import com.orbit.organization.application.port.out.ActiveTechnicianContract;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.MembershipId;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.TechnicianId;
import com.orbit.support.TestcontainersConfiguration;

/** 상태를 바꾸는 유즈케이스가 아직 없어 소속·기사 계약 행은 SQL로 넣는다. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("활성 직원 소속·기사 계약 한 문장 조회")
class ActiveMemberQueryAdapterTest {

    private static final Instant JOINED_AT = Instant.parse("2026-10-01T00:00:00.123456Z");
    private static final Instant CHANGED_AT = Instant.parse("2026-10-05T00:00:00.654321Z");
    private static final OrganizationId ORGANIZATION_ID = new OrganizationId(100L);
    private static final OrganizationId OTHER_ORGANIZATION_ID = new OrganizationId(200L);
    private static final AccountId ACCOUNT_ID = new AccountId(7L);

    @Autowired
    private EntityManager entityManager;

    private ActiveMemberQueryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ActiveMemberQueryAdapter(entityManager);
    }

    @Test
    @DisplayName("활성 직원 소속을 총관리자 표시와 함께 찾는다")
    void findsActiveMembership() {
        long id = insertMembership(ORGANIZATION_ID, ACCOUNT_ID, true, "ACTIVE");

        assertThat(adapter.findActiveMembers(ORGANIZATION_ID, ACCOUNT_ID))
                .containsExactly(new ActiveMembership(new MembershipId(id), true));
    }

    @Test
    @DisplayName("활성 기사 계약을 찾는다")
    void findsActiveTechnicianContract() {
        long id = insertTechnician(ORGANIZATION_ID, ACCOUNT_ID, "ACTIVE");

        assertThat(adapter.findActiveMembers(ORGANIZATION_ID, ACCOUNT_ID))
                .containsExactly(new ActiveTechnicianContract(new TechnicianId(id)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"DEACTIVATED", "ROLE_CHANGED"})
    @DisplayName("비활성·역할 변경 종료 소속과 기사 계약은 찾지 않는다")
    void ignoresInactiveMembershipAndTechnicianContract(String status) {
        insertMembership(ORGANIZATION_ID, ACCOUNT_ID, false, status);
        insertTechnician(ORGANIZATION_ID, ACCOUNT_ID, status);

        assertThat(adapter.findActiveMembers(ORGANIZATION_ID, ACCOUNT_ID)).isEmpty();
    }

    @Test
    @DisplayName("다른 발주사의 소속과 기사 계약은 찾지 않는다")
    void ignoresMembersOfOtherOrganization() {
        insertMembership(OTHER_ORGANIZATION_ID, ACCOUNT_ID, false, "ACTIVE");
        insertTechnician(OTHER_ORGANIZATION_ID, ACCOUNT_ID, "ACTIVE");

        assertThat(adapter.findActiveMembers(ORGANIZATION_ID, ACCOUNT_ID)).isEmpty();
    }

    @Test
    @DisplayName("활성 소속과 활성 기사 계약이 함께 있으면 둘 다 돌려줘 호출자가 불변식 위반을 알게 한다")
    void returnsBothWhenMembershipAndTechnicianContractAreActive() {
        long membershipId = insertMembership(ORGANIZATION_ID, ACCOUNT_ID, false, "ACTIVE");
        long technicianId = insertTechnician(ORGANIZATION_ID, ACCOUNT_ID, "ACTIVE");

        assertThat(adapter.findActiveMembers(ORGANIZATION_ID, ACCOUNT_ID))
                .containsExactlyInAnyOrder(
                        new ActiveMembership(new MembershipId(membershipId), false),
                        new ActiveTechnicianContract(new TechnicianId(technicianId)));
    }

    @Test
    @DisplayName("같은 계정·같은 발주사의 기사 계약은 하나만 둘 수 있다")
    void rejectsSecondTechnicianOfSameAccountInSameOrganization() {
        insertTechnician(ORGANIZATION_ID, ACCOUNT_ID, "ROLE_CHANGED");

        assertThatThrownBy(() -> insertTechnician(ORGANIZATION_ID, ACCOUNT_ID, "ACTIVE"))
                .isInstanceOf(PersistenceException.class);
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
                .setParameter(5, JOINED_AT)
                .setParameter(6, CHANGED_AT)
                .setParameter(7, JOINED_AT)
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

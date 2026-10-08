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

import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.Membership;
import com.orbit.organization.domain.MembershipStatus;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.Technician;
import com.orbit.organization.domain.TechnicianId;
import com.orbit.organization.domain.TechnicianStatus;
import com.orbit.support.TestcontainersConfiguration;

/** 상태를 바꾸는 유즈케이스가 아직 없어 비활성·역할 변경 종료 행은 SQL로 넣는다. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("활성 직원 소속·기사 계약 조회")
class ActiveMemberPersistenceAdapterTest {

    private static final Instant JOINED_AT = Instant.parse("2026-10-01T00:00:00.123456Z");
    private static final Instant CHANGED_AT = Instant.parse("2026-10-05T00:00:00.654321Z");
    private static final OrganizationId ORGANIZATION_ID = new OrganizationId(100L);
    private static final OrganizationId OTHER_ORGANIZATION_ID = new OrganizationId(200L);
    private static final AccountId ACCOUNT_ID = new AccountId(7L);

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private SpringDataMembershipRepository membershipRepository;

    @Autowired
    private SpringDataTechnicianRepository technicianRepository;

    private MembershipPersistenceAdapter memberships;
    private TechnicianPersistenceAdapter technicians;

    @BeforeEach
    void setUp() {
        memberships = new MembershipPersistenceAdapter(membershipRepository);
        technicians = new TechnicianPersistenceAdapter(technicianRepository);
    }

    @Test
    @DisplayName("활성 직원 소속을 총관리자 표시·시각과 함께 찾는다")
    void findsActiveMembership() {
        long id = insertMembership(ORGANIZATION_ID, ACCOUNT_ID, true, "ACTIVE");

        Membership found = memberships.findActive(ORGANIZATION_ID, ACCOUNT_ID).orElseThrow();

        assertThat(found.id()).hasValueSatisfying(membershipId -> assertThat(membershipId.value())
                .isEqualTo(id));
        assertThat(found.isOwner()).isTrue();
        assertThat(found.status()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(found.joinedAt()).isEqualTo(JOINED_AT);
        assertThat(found.statusChangedAt()).isEqualTo(CHANGED_AT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DEACTIVATED", "ROLE_CHANGED"})
    @DisplayName("비활성·역할 변경 종료 직원 소속은 찾지 않는다")
    void ignoresInactiveMembership(String status) {
        insertMembership(ORGANIZATION_ID, ACCOUNT_ID, false, status);

        assertThat(memberships.findActive(ORGANIZATION_ID, ACCOUNT_ID)).isEmpty();
    }

    @Test
    @DisplayName("다른 발주사의 직원 소속은 찾지 않는다")
    void ignoresMembershipOfOtherOrganization() {
        insertMembership(OTHER_ORGANIZATION_ID, ACCOUNT_ID, false, "ACTIVE");

        assertThat(memberships.findActive(ORGANIZATION_ID, ACCOUNT_ID)).isEmpty();
    }

    @Test
    @DisplayName("활성 기사 계약을 시각과 함께 찾는다")
    void findsActiveTechnician() {
        long id = insertTechnician(ORGANIZATION_ID, ACCOUNT_ID, "ACTIVE");

        Technician found = technicians.findActive(ORGANIZATION_ID, ACCOUNT_ID).orElseThrow();

        assertThat(found.id()).isEqualTo(new TechnicianId(id));
        assertThat(found.organizationId()).isEqualTo(ORGANIZATION_ID);
        assertThat(found.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(found.status()).isEqualTo(TechnicianStatus.ACTIVE);
        assertThat(found.contractedAt()).isEqualTo(JOINED_AT);
        assertThat(found.statusChangedAt()).isEqualTo(CHANGED_AT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DEACTIVATED", "ROLE_CHANGED"})
    @DisplayName("비활성·역할 변경 종료 기사 계약은 찾지 않는다")
    void ignoresInactiveTechnician(String status) {
        insertTechnician(ORGANIZATION_ID, ACCOUNT_ID, status);

        assertThat(technicians.findActive(ORGANIZATION_ID, ACCOUNT_ID)).isEmpty();
    }

    @Test
    @DisplayName("다른 발주사의 기사 계약은 찾지 않는다")
    void ignoresTechnicianOfOtherOrganization() {
        insertTechnician(OTHER_ORGANIZATION_ID, ACCOUNT_ID, "ACTIVE");

        assertThat(technicians.findActive(ORGANIZATION_ID, ACCOUNT_ID)).isEmpty();
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

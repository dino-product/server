package com.orbit.organization.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.Membership;
import com.orbit.organization.domain.MembershipStatus;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.Technician;
import com.orbit.organization.domain.TechnicianStatus;
import com.orbit.support.PersistenceTestConfiguration;

/** 세부 유형은 소속·계약 도메인에 없으므로 유형이 지정된 행은 SQL로 넣는다. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PersistenceTestConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("탈퇴한 계정의 직원 소속·기사 계약 저장")
class MemberWithdrawalPersistenceAdapterTest {

    private static final Instant JOINED_AT = Instant.parse("2026-10-01T00:00:00.123456Z");
    private static final Instant DEACTIVATED_AT = Instant.parse("2026-10-03T00:00:00.123456Z");
    private static final Instant WITHDRAWN_AT = Instant.parse("2026-10-06T00:00:00.123456Z");
    private static final AccountId ACCOUNT_ID = new AccountId(7L);
    private static final long CATEGORY_ID = 77L;

    @Autowired
    private SpringDataMembershipRepository membershipRepository;

    @Autowired
    private SpringDataTechnicianRepository technicianRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MembershipPersistenceAdapter memberships;
    private TechnicianPersistenceAdapter technicians;

    @BeforeEach
    void setUp() {
        memberships = new MembershipPersistenceAdapter(membershipRepository);
        technicians = new TechnicianPersistenceAdapter(technicianRepository);
    }

    @Test
    @DisplayName("계정의 직원 소속·기사 계약이 있는 발주사를 상태와 관계없이 식별자 오름차순으로 찾는다")
    void listsOrganizationsOfAccountRegardlessOfStatus() {
        insertMembership(new OrganizationId(12L), ACCOUNT_ID, "ACTIVE");
        insertMembership(new OrganizationId(10L), ACCOUNT_ID, "DEACTIVATED");
        insertMembership(new OrganizationId(11L), new AccountId(8L), "ACTIVE");
        insertTechnician(new OrganizationId(14L), ACCOUNT_ID, "DEACTIVATED");
        insertTechnician(new OrganizationId(13L), ACCOUNT_ID, "ACTIVE");

        assertThat(memberships.listOrganizationsOf(ACCOUNT_ID))
                .containsExactly(new OrganizationId(10L), new OrganizationId(12L));
        assertThat(technicians.listOrganizationsOf(ACCOUNT_ID))
                .containsExactly(new OrganizationId(13L), new OrganizationId(14L));
        assertThat(memberships.listOrganizationsOf(new AccountId(9L))).isEmpty();
        assertThat(technicians.listOrganizationsOf(new AccountId(9L))).isEmpty();
    }

    @Test
    @DisplayName("탈퇴한 직원 소속은 상태·상태 변경 시각·탈퇴 시각만 바꾸고 총관리자 표시·참여일·세부 유형은 그대로 둔다")
    void savesWithdrawnMembership() {
        Long id = insertMembership(new OrganizationId(10L), ACCOUNT_ID, "ACTIVE");
        Membership found = memberships.find(new OrganizationId(10L), ACCOUNT_ID).orElseThrow();

        memberships.withdraw(found.withdraw(WITHDRAWN_AT));
        entityManager.flush();
        entityManager.clear();

        assertThat(jdbcTemplate.queryForMap(
                        "select status, is_owner, joined_at, status_changed_at, withdrawn_at,"
                                + " company_membership_category_id from company_memberships where id = ?",
                        id))
                .containsEntry("status", "DEACTIVATED")
                .containsEntry("is_owner", true)
                .containsEntry("joined_at", Timestamp.from(JOINED_AT))
                .containsEntry("status_changed_at", Timestamp.from(WITHDRAWN_AT))
                .containsEntry("withdrawn_at", Timestamp.from(WITHDRAWN_AT))
                .containsEntry("company_membership_category_id", CATEGORY_ID);
        assertThat(memberships.find(new OrganizationId(10L), ACCOUNT_ID)).hasValueSatisfying(membership -> {
            assertThat(membership.status()).isEqualTo(MembershipStatus.DEACTIVATED);
            assertThat(membership.withdrawnAt()).contains(WITHDRAWN_AT);
        });
    }

    @Test
    @DisplayName("탈퇴한 기사 계약은 같은 행의 상태·상태 변경 시각·탈퇴 시각만 바꾸고 참여일·세부 유형은 그대로 둔다")
    void savesWithdrawnTechnician() {
        Long id = insertTechnician(new OrganizationId(10L), ACCOUNT_ID, "DEACTIVATED");
        Technician found = technicians.find(new OrganizationId(10L), ACCOUNT_ID).orElseThrow();

        Technician saved = technicians.withdraw(found.withdraw(WITHDRAWN_AT));
        entityManager.flush();
        entityManager.clear();

        assertThat(saved.id().orElseThrow().value()).isEqualTo(id);
        assertThat(jdbcTemplate.queryForMap(
                        "select status, joined_at, status_changed_at, withdrawn_at, company_technician_category_id"
                                + " from technician where id = ?",
                        id))
                .containsEntry("status", "DEACTIVATED")
                .containsEntry("joined_at", Timestamp.from(JOINED_AT))
                .containsEntry("status_changed_at", Timestamp.from(DEACTIVATED_AT))
                .containsEntry("withdrawn_at", Timestamp.from(WITHDRAWN_AT))
                .containsEntry("company_technician_category_id", CATEGORY_ID);
        assertThat(technicians.find(new OrganizationId(10L), ACCOUNT_ID)).hasValueSatisfying(technician -> {
            assertThat(technician.status()).isEqualTo(TechnicianStatus.DEACTIVATED);
            assertThat(technician.withdrawnAt()).contains(WITHDRAWN_AT);
        });
    }

    @Test
    @DisplayName("새로 만든 직원 소속·기사 계약에는 탈퇴 시각이 없다")
    void newRowsAreNotWithdrawn() {
        Membership membership = memberships.save(Membership.join(new OrganizationId(10L), ACCOUNT_ID, JOINED_AT));
        Technician technician = technicians.save(Technician.join(new OrganizationId(11L), ACCOUNT_ID, JOINED_AT));
        entityManager.flush();
        entityManager.clear();

        assertThat(jdbcTemplate.queryForObject(
                        "select withdrawn_at from company_memberships where id = ?",
                        Timestamp.class,
                        membership.id().orElseThrow().value()))
                .isNull();
        assertThat(technicians
                        .find(new OrganizationId(11L), ACCOUNT_ID)
                        .orElseThrow()
                        .isWithdrawn())
                .isFalse();
        assertThat(technician.isWithdrawn()).isFalse();
    }

    private Long insertMembership(OrganizationId organizationId, AccountId accountId, String status) {
        return jdbcTemplate.queryForObject(
                "insert into company_memberships (company_id, member_id, is_owner, company_membership_category_id,"
                        + " status, joined_at, status_changed_at, created_at, updated_at)"
                        + " values (?, ?, true, ?, ?, ?, ?, now(), now()) returning id",
                Long.class,
                organizationId.value(),
                accountId.value(),
                CATEGORY_ID,
                status,
                Timestamp.from(JOINED_AT),
                Timestamp.from(status.equals("ACTIVE") ? JOINED_AT : DEACTIVATED_AT));
    }

    private Long insertTechnician(OrganizationId organizationId, AccountId accountId, String status) {
        return jdbcTemplate.queryForObject(
                "insert into technician (company_id, member_id, company_technician_category_id, status, joined_at,"
                        + " status_changed_at, created_at, updated_at)"
                        + " values (?, ?, ?, ?, ?, ?, now(), now()) returning id",
                Long.class,
                organizationId.value(),
                accountId.value(),
                CATEGORY_ID,
                status,
                Timestamp.from(JOINED_AT),
                Timestamp.from(status.equals("ACTIVE") ? JOINED_AT : DEACTIVATED_AT));
    }
}

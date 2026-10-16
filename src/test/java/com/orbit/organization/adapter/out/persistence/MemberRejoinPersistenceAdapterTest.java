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
import com.orbit.organization.domain.MembershipStatus;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.Technician;
import com.orbit.organization.domain.TechnicianStatus;
import com.orbit.support.PersistenceTestConfiguration;

/** 세부 유형은 소속·계약 도메인에 없으므로 유형이 지정된 비활성 행은 SQL로 넣는다. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PersistenceTestConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("재참여 승인의 직원 소속·기사 계약 저장")
class MemberRejoinPersistenceAdapterTest {

    private static final Instant JOINED_AT = Instant.parse("2026-10-01T00:00:00.123456Z");
    private static final Instant DEACTIVATED_AT = Instant.parse("2026-10-03T00:00:00.123456Z");
    private static final Instant REJOINED_AT = Instant.parse("2026-10-06T00:00:00.123456Z");
    private static final OrganizationId ORGANIZATION_ID = new OrganizationId(10L);
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
    @DisplayName("계정의 그 발주사 직원 소속·기사 계약을 상태와 관계없이 찾고, 다른 계정·발주사의 행은 찾지 않는다")
    void findsRowsRegardlessOfStatus() {
        insertMembership(ORGANIZATION_ID, ACCOUNT_ID, true);
        insertTechnician(ORGANIZATION_ID, ACCOUNT_ID);

        assertThat(memberships.find(ORGANIZATION_ID, ACCOUNT_ID)).hasValueSatisfying(membership -> {
            assertThat(membership.status()).isEqualTo(MembershipStatus.DEACTIVATED);
            assertThat(membership.isOwner()).isTrue();
        });
        assertThat(technicians.find(ORGANIZATION_ID, ACCOUNT_ID)).hasValueSatisfying(technician -> {
            assertThat(technician.status()).isEqualTo(TechnicianStatus.DEACTIVATED);
            assertThat(technician.joinedAt()).isEqualTo(JOINED_AT);
            assertThat(technician.statusChangedAt()).isEqualTo(DEACTIVATED_AT);
        });
        assertThat(memberships.find(ORGANIZATION_ID, new AccountId(8L))).isEmpty();
        assertThat(technicians.find(new OrganizationId(11L), ACCOUNT_ID)).isEmpty();
    }

    @Test
    @DisplayName("직원 소속을 되살리면 상태·상태 변경 시각·총관리자 표시만 바꾸고, 세부 유형은 해제를 요청할 때만 지운다")
    void reactivatesMembership() {
        Long kept = insertMembership(ORGANIZATION_ID, ACCOUNT_ID, true);
        Long cleared = insertMembership(ORGANIZATION_ID, new AccountId(8L), false);

        memberships.reactivate(
                memberships.find(ORGANIZATION_ID, ACCOUNT_ID).orElseThrow().rejoin(REJOINED_AT), false);
        memberships.reactivate(
                memberships
                        .find(ORGANIZATION_ID, new AccountId(8L))
                        .orElseThrow()
                        .rejoin(REJOINED_AT),
                true);
        entityManager.flush();
        entityManager.clear();

        assertThat(jdbcTemplate.queryForMap(
                        "select status, is_owner, joined_at, status_changed_at, company_membership_category_id"
                                + " from company_memberships where id = ?",
                        kept))
                .containsEntry("status", "ACTIVE")
                .containsEntry("is_owner", false)
                .containsEntry("joined_at", Timestamp.from(JOINED_AT))
                .containsEntry("status_changed_at", Timestamp.from(REJOINED_AT))
                .containsEntry("company_membership_category_id", CATEGORY_ID);
        assertThat(jdbcTemplate.queryForObject(
                        "select company_membership_category_id from company_memberships where id = ?",
                        Long.class,
                        cleared))
                .isNull();
    }

    @Test
    @DisplayName("기사 계약을 되살리면 같은 행의 상태·상태 변경 시각만 바꾸고 참여일은 그대로 두며, 세부 유형은 해제를 요청할 때만 지운다")
    void reactivatesTechnician() {
        Long kept = insertTechnician(ORGANIZATION_ID, ACCOUNT_ID);
        Long cleared = insertTechnician(ORGANIZATION_ID, new AccountId(8L));

        Technician reactivated = technicians.reactivate(
                technicians.find(ORGANIZATION_ID, ACCOUNT_ID).orElseThrow().rejoin(REJOINED_AT), false);
        technicians.reactivate(
                technicians
                        .find(ORGANIZATION_ID, new AccountId(8L))
                        .orElseThrow()
                        .rejoin(REJOINED_AT),
                true);
        entityManager.flush();
        entityManager.clear();

        assertThat(reactivated.id().orElseThrow().value()).isEqualTo(kept);
        assertThat(jdbcTemplate.queryForMap(
                        "select status, joined_at, status_changed_at, company_technician_category_id"
                                + " from technician where id = ?",
                        kept))
                .containsEntry("status", "ACTIVE")
                .containsEntry("joined_at", Timestamp.from(JOINED_AT))
                .containsEntry("status_changed_at", Timestamp.from(REJOINED_AT))
                .containsEntry("company_technician_category_id", CATEGORY_ID);
        assertThat(jdbcTemplate.queryForObject(
                        "select company_technician_category_id from technician where id = ?", Long.class, cleared))
                .isNull();
    }

    @Test
    @DisplayName("새 기사 계약은 참여일을 감사 컬럼과 따로 저장한다")
    void savesTechnicianJoinDateSeparatelyFromAuditColumn() {
        Technician saved = technicians.save(Technician.join(ORGANIZATION_ID, ACCOUNT_ID, JOINED_AT, REJOINED_AT));
        entityManager.flush();

        assertThat(jdbcTemplate.queryForMap(
                        "select joined_at, status_changed_at from technician where id = ?",
                        saved.id().orElseThrow().value()))
                .containsEntry("joined_at", Timestamp.from(JOINED_AT))
                .containsEntry("status_changed_at", Timestamp.from(REJOINED_AT));
    }

    private Long insertMembership(OrganizationId organizationId, AccountId accountId, boolean owner) {
        return jdbcTemplate.queryForObject(
                "insert into company_memberships (company_id, member_id, is_owner, company_membership_category_id,"
                        + " status, joined_at, status_changed_at, created_at, updated_at)"
                        + " values (?, ?, ?, ?, 'DEACTIVATED', ?, ?, now(), now()) returning id",
                Long.class,
                organizationId.value(),
                accountId.value(),
                owner,
                CATEGORY_ID,
                Timestamp.from(JOINED_AT),
                Timestamp.from(DEACTIVATED_AT));
    }

    private Long insertTechnician(OrganizationId organizationId, AccountId accountId) {
        return jdbcTemplate.queryForObject(
                "insert into technician (company_id, member_id, company_technician_category_id, status, joined_at,"
                        + " status_changed_at, created_at, updated_at)"
                        + " values (?, ?, ?, 'DEACTIVATED', ?, ?, now(), now()) returning id",
                Long.class,
                organizationId.value(),
                accountId.value(),
                CATEGORY_ID,
                Timestamp.from(JOINED_AT),
                Timestamp.from(DEACTIVATED_AT));
    }
}

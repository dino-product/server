package com.orbit.organization.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.orbit.organization.application.port.out.DirectoryCategory;
import com.orbit.organization.application.port.out.DirectoryStaff;
import com.orbit.organization.application.port.out.DirectoryTechnician;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.MemberCategoryId;
import com.orbit.organization.domain.MembershipId;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.TechnicianId;
import com.orbit.support.PersistenceAdapterTestSupport;

/** 참여 요청 승인과 유형 지정 유즈케이스가 develop에 아직 없어 소속·기사 계약·유형 행은 SQL로 넣는다. */
@DisplayName("구성원 표시 정보 조회(PostgreSQL)")
class MemberDirectoryQueryAdapterTest extends PersistenceAdapterTestSupport {

    private static final Instant AT = Instant.parse("2026-10-01T00:00:00Z");
    private static final OrganizationId ORGANIZATION = new OrganizationId(100L);
    private static final OrganizationId OTHER_ORGANIZATION = new OrganizationId(200L);

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MemberDirectoryQueryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new MemberDirectoryQueryAdapter(entityManager);
    }

    @Test
    @DisplayName("직원 소속은 활성 여부와 관계없이 계정·총관리자 여부·삭제되지 않은 직원 유형과 함께 찾는다")
    void findsStaffWithUndeletedCategoryRegardlessOfStatus() {
        long category = insertCategory("company_membership_category", ORGANIZATION, "정규직", false);
        long deletedCategory = insertCategory("company_membership_category", ORGANIZATION, "계약직", true);
        long owner = insertMembership(ORGANIZATION, 1L, true, "ACTIVE", category);
        long deactivated = insertMembership(ORGANIZATION, 2L, false, "DEACTIVATED", deletedCategory);
        long uncategorized = insertMembership(ORGANIZATION, 3L, false, "ACTIVE", null);
        long other = insertMembership(OTHER_ORGANIZATION, 4L, false, "ACTIVE", null);

        assertThat(adapter.findStaff(
                        ORGANIZATION,
                        Set.of(
                                new MembershipId(owner),
                                new MembershipId(deactivated),
                                new MembershipId(uncategorized),
                                new MembershipId(other))))
                .containsExactlyInAnyOrder(
                        new DirectoryStaff(
                                new MembershipId(owner),
                                new AccountId(1L),
                                true,
                                new DirectoryCategory(new MemberCategoryId(category), "정규직", "#3B82F6")),
                        new DirectoryStaff(new MembershipId(deactivated), new AccountId(2L), false, null),
                        new DirectoryStaff(new MembershipId(uncategorized), new AccountId(3L), false, null));
    }

    @Test
    @DisplayName("기사 계약은 활성 여부와 관계없이 찾고, 활성 목록은 그 발주사의 활성 계약만 담는다")
    void findsTechniciansAndListsActiveOnes() {
        long category = insertCategory("company_technician_category", ORGANIZATION, "전기 기사", false);
        long otherCategory = insertCategory("company_technician_category", OTHER_ORGANIZATION, "남의 유형", false);
        long active = insertTechnician(ORGANIZATION, 1L, "ACTIVE", category);
        long deactivated = insertTechnician(ORGANIZATION, 2L, "DEACTIVATED", null);
        long wrongCategory = insertTechnician(ORGANIZATION, 3L, "ACTIVE", otherCategory);
        long other = insertTechnician(OTHER_ORGANIZATION, 4L, "ACTIVE", null);
        DirectoryTechnician activeEntry = new DirectoryTechnician(
                new TechnicianId(active),
                new AccountId(1L),
                new DirectoryCategory(new MemberCategoryId(category), "전기 기사", "#3B82F6"));
        DirectoryTechnician wrongCategoryEntry =
                new DirectoryTechnician(new TechnicianId(wrongCategory), new AccountId(3L), null);

        assertThat(adapter.findTechnicians(
                        ORGANIZATION,
                        Set.of(new TechnicianId(active), new TechnicianId(deactivated), new TechnicianId(other))))
                .containsExactlyInAnyOrder(
                        activeEntry, new DirectoryTechnician(new TechnicianId(deactivated), new AccountId(2L), null));
        assertThat(adapter.listActiveTechnicians(ORGANIZATION))
                .containsExactlyInAnyOrder(activeEntry, wrongCategoryEntry);
    }

    private long insertCategory(String table, OrganizationId organizationId, String name, boolean deleted) {
        jdbcTemplate.update(
                "insert into " + table + " (company_id, name, color, is_deleted, created_at, updated_at, deleted_at)"
                        + " values (?, ?, '#3B82F6', ?, ?, ?, ?)",
                organizationId.value(),
                name,
                deleted,
                Timestamp.from(AT),
                Timestamp.from(AT),
                deleted ? Timestamp.from(AT) : null);
        return jdbcTemplate.queryForObject("select max(id) from " + table, Long.class);
    }

    private long insertMembership(
            OrganizationId organizationId, long accountId, boolean owner, String status, Long categoryId) {
        jdbcTemplate.update(
                "insert into company_memberships (company_id, member_id, is_owner, status,"
                        + " company_membership_category_id, joined_at, status_changed_at, created_at, updated_at)"
                        + " values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                organizationId.value(),
                accountId,
                owner,
                status,
                categoryId,
                Timestamp.from(AT),
                Timestamp.from(AT),
                Timestamp.from(AT),
                Timestamp.from(AT));
        return jdbcTemplate.queryForObject("select max(id) from company_memberships", Long.class);
    }

    private long insertTechnician(OrganizationId organizationId, long accountId, String status, Long categoryId) {
        jdbcTemplate.update(
                "insert into technician (company_id, member_id, company_technician_category_id, status,"
                        + " status_changed_at, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?)",
                organizationId.value(),
                accountId,
                categoryId,
                status,
                Timestamp.from(AT),
                Timestamp.from(AT),
                Timestamp.from(AT));
        return jdbcTemplate.queryForObject("select max(id) from technician", Long.class);
    }
}

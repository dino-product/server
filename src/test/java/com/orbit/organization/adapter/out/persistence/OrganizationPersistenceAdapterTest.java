package com.orbit.organization.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.CompanyCode;
import com.orbit.organization.domain.Industry;
import com.orbit.organization.domain.Membership;
import com.orbit.organization.domain.MembershipId;
import com.orbit.organization.domain.MembershipStatus;
import com.orbit.organization.domain.Organization;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.OrganizationName;
import com.orbit.support.TestcontainersConfiguration;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("발주사·직원 소속 저장")
class OrganizationPersistenceAdapterTest {

    private static final Instant NOW = Instant.parse("2026-10-06T00:00:00.123456Z");
    private static final Instant LATER = Instant.parse("2026-10-08T09:30:00.654321Z");

    @Autowired
    private SpringDataOrganizationRepository organizationRepository;

    @Autowired
    private SpringDataMembershipRepository membershipRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private OrganizationPersistenceAdapter organizations;
    private MembershipPersistenceAdapter memberships;

    @BeforeEach
    void setUp() {
        organizations = new OrganizationPersistenceAdapter(organizationRepository);
        memberships = new MembershipPersistenceAdapter(membershipRepository, Clock.fixed(LATER, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("발주사를 저장하면 식별자와 함께 같은 값으로 복원된다")
    void savesAndRestoresOrganization() {
        Organization saved = organizations.save(organization("7K2M9X"));

        assertThat(saved.id()).isPresent();
        assertThat(saved.name()).isEqualTo(new OrganizationName("오르빗 설비"));
        assertThat(saved.industry()).isEqualTo(Industry.PLUMBING);
        assertThat(saved.code()).isEqualTo(new CompanyCode("7K2M9X"));
        assertThat(saved.createdAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("현재 쓰이는 회사 코드인지 확인한다")
    void checksWhetherCodeIsInUse() {
        organizations.save(organization("Q4ZT8B"));

        assertThat(organizations.existsByCode(new CompanyCode("Q4ZT8B"))).isTrue();
        assertThat(organizations.existsByCode(new CompanyCode("Q4ZT8C"))).isFalse();
    }

    @Test
    @DisplayName("같은 회사 코드를 두 발주사가 가질 수 없다")
    void rejectsDuplicateCodeAtDatabase() {
        organizations.save(organization("M3N4P5"));

        assertThatThrownBy(() -> {
                    organizations.save(organization("M3N4P5"));
                    organizationRepository.flush();
                })
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("직원 소속을 저장하면 총관리자 표시·상태·시각을 그대로 복원한다")
    void savesAndRestoresMembership() {
        OrganizationId organizationId =
                organizations.save(organization("R6S7T8")).id().orElseThrow();

        Membership saved = memberships.save(Membership.founder(organizationId, new AccountId(7L), NOW));

        assertThat(saved.id()).isPresent();
        assertThat(saved.organizationId()).isEqualTo(organizationId);
        assertThat(saved.accountId()).isEqualTo(new AccountId(7L));
        assertThat(saved.isOwner()).isTrue();
        assertThat(saved.status()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(saved.joinedAt()).isEqualTo(NOW);
        assertThat(saved.statusChangedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("같은 계정·같은 발주사의 직원 소속은 하나만 둘 수 있다")
    void rejectsSecondMembershipOfSameAccountInSameOrganization() {
        OrganizationId organizationId =
                organizations.save(organization("V9W0X1")).id().orElseThrow();
        memberships.save(Membership.founder(organizationId, new AccountId(7L), NOW));

        assertThatThrownBy(() -> {
                    memberships.save(Membership.founder(organizationId, new AccountId(7L), NOW));
                    membershipRepository.flush();
                })
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("식별자로 직원 소속을 찾는다")
    void findsMembershipById() {
        OrganizationId organizationId =
                organizations.save(organization("B2C3D4")).id().orElseThrow();
        Membership saved = memberships.save(Membership.founder(organizationId, new AccountId(7L), NOW));

        assertThat(memberships.findById(saved.id().orElseThrow()))
                .hasValueSatisfying(found -> assertThat(found.accountId()).isEqualTo(new AccountId(7L)));
        assertThat(memberships.findById(new MembershipId(Long.MAX_VALUE))).isEmpty();
    }

    @Test
    @DisplayName("계정의 그 발주사 활성 소속만 찾는다")
    void findsActiveMembershipOfAccountInOrganization() {
        OrganizationId organizationId =
                organizations.save(organization("E5F6G7")).id().orElseThrow();
        OrganizationId otherId = organizations.save(organization("H8J9K0")).id().orElseThrow();
        memberships.save(Membership.founder(organizationId, new AccountId(7L), NOW));

        assertThat(memberships.findActive(organizationId, new AccountId(7L))).isPresent();
        assertThat(memberships.findActive(otherId, new AccountId(7L))).isEmpty();
        assertThat(memberships.findActive(organizationId, new AccountId(8L))).isEmpty();
    }

    @Test
    @DisplayName("저장된 직원 소속을 다시 저장하면 총관리자 표시와 수정 시각을 바꾼다")
    void updatesOwnerMarkOfStoredMembership() {
        OrganizationId organizationId =
                organizations.save(organization("M1N2P3")).id().orElseThrow();
        MembershipId staffId = staffMembership(organizationId, 8L);
        Membership staff = memberships.findById(staffId).orElseThrow();

        Membership saved = memberships.save(staff.designateAsOwner());
        membershipRepository.flush();

        assertThat(saved.isOwner()).isTrue();
        assertThat(jdbcTemplate.queryForMap(
                        "select is_owner, joined_at, updated_at from company_memberships where id = ?",
                        staffId.value()))
                .containsEntry("is_owner", true)
                .containsEntry("joined_at", Timestamp.from(NOW))
                .containsEntry("updated_at", Timestamp.from(LATER));
    }

    @Test
    @DisplayName("그 발주사의 활성 총관리자 수를 센다")
    void countsActiveOwnersOfOrganization() {
        OrganizationId organizationId =
                organizations.save(organization("Q1R2S3")).id().orElseThrow();
        OrganizationId otherId = organizations.save(organization("T4V5W6")).id().orElseThrow();
        memberships.save(Membership.founder(organizationId, new AccountId(7L), NOW));
        memberships.save(Membership.founder(organizationId, new AccountId(8L), NOW));
        staffMembership(organizationId, 9L);
        memberships.save(Membership.founder(otherId, new AccountId(7L), NOW));

        assertThat(memberships.countActiveOwners(organizationId)).isEqualTo(2);
    }

    @Test
    @DisplayName("발주사가 없어도 잠금 요청은 실패하지 않는다")
    void lockingMissingOrganizationDoesNotFail() {
        organizations.lock(new OrganizationId(Long.MAX_VALUE));
    }

    private MembershipId staffMembership(OrganizationId organizationId, long accountId) {
        jdbcTemplate.update(
                "insert into company_memberships"
                        + " (company_id, member_id, is_owner, status, joined_at, status_changed_at, created_at, updated_at)"
                        + " values (?, ?, false, 'ACTIVE', ?, ?, ?, ?)",
                organizationId.value(),
                accountId,
                Timestamp.from(NOW),
                Timestamp.from(NOW),
                Timestamp.from(NOW),
                Timestamp.from(NOW));
        return new MembershipId(jdbcTemplate.queryForObject(
                "select id from company_memberships where company_id = ? and member_id = ?",
                Long.class,
                organizationId.value(),
                accountId));
    }

    private static Organization organization(String code) {
        return Organization.create(new OrganizationName("오르빗 설비"), Industry.PLUMBING, new CompanyCode(code), NOW);
    }
}

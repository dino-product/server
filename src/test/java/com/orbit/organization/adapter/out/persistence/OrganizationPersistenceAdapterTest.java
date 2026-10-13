package com.orbit.organization.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
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
import com.orbit.organization.domain.RetiredCompanyCode;
import com.orbit.support.PersistenceTestConfiguration;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PersistenceTestConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("발주사·직원 소속 저장")
class OrganizationPersistenceAdapterTest {

    private static final Instant NOW = Instant.parse("2026-10-06T00:00:00.123456Z");

    @Autowired
    private SpringDataOrganizationRepository organizationRepository;

    @Autowired
    private SpringDataRetiredCompanyCodeRepository retiredCodeRepository;

    @Autowired
    private SpringDataMembershipRepository membershipRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private OrganizationPersistenceAdapter organizations;
    private MembershipPersistenceAdapter memberships;

    @BeforeEach
    void setUp() {
        organizations = new OrganizationPersistenceAdapter(
                organizationRepository, retiredCodeRepository, jdbcTemplate, new OrganizationLockProperties(null));
        memberships = new MembershipPersistenceAdapter(membershipRepository);
    }

    @Test
    @DisplayName("발주사를 잠근 뒤에는 트랜잭션이 앞서 정한 대기 한도를 그대로 되돌린다")
    void restoresPreviousLockTimeoutAfterLocking() {
        OrganizationId id = organizations.save(organization("7K2M9X")).id().orElseThrow();
        jdbcTemplate.execute("SET LOCAL lock_timeout = '7s'");

        organizations.lock(id);

        assertThat(jdbcTemplate.queryForObject("SELECT current_setting('lock_timeout')", String.class))
                .isEqualTo("7s");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("트랜잭션 밖에서는 곧바로 풀리는 잠금이 되므로 거부한다")
    void rejectsLockingOutsideTransaction() {
        assertThatThrownBy(() -> organizations.lock(new OrganizationId(1L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("organization lock requires an active transaction");
    }

    @Test
    @DisplayName("발주사를 저장하면 식별자와 함께 같은 값으로 복원되고 생성·수정 시각이 채워진다")
    void savesAndRestoresOrganization() {
        Organization saved = organizations.save(organization("7K2M9X"));
        organizationRepository.flush();

        assertThat(saved.id()).isPresent();
        assertThat(saved.name()).isEqualTo(new OrganizationName("오르빗 설비"));
        assertThat(saved.industry()).isEqualTo(Industry.PLUMBING);
        assertThat(saved.code()).isEqualTo(new CompanyCode("7K2M9X"));
        assertThat(jdbcTemplate.queryForMap(
                        "select created_at, updated_at from companies where id = ?",
                        saved.id().orElseThrow().value()))
                .allSatisfy((column, value) -> assertThat(value).isNotNull());
    }

    @Test
    @DisplayName("현재 코드나 폐기 코드로 발급된 적 있는 회사 코드인지 확인한다")
    void checksWhetherCodeWasIssued() {
        Organization organization = organizations.save(organization("Q4ZT8B"));
        organizations.saveRetiredCode(organization.changeCode(new CompanyCode("Q4ZT8C"), NOW.plusSeconds(60)));
        organizations.save(organization);
        organizationRepository.flush();

        assertThat(organizations.existsIssuedCode(new CompanyCode("Q4ZT8B"))).isTrue();
        assertThat(organizations.existsIssuedCode(new CompanyCode("Q4ZT8C"))).isTrue();
        assertThat(organizations.existsIssuedCode(new CompanyCode("Q4ZT8D"))).isFalse();
    }

    @Test
    @DisplayName("현재 코드로만 발주사를 찾고, 폐기 코드인지는 따로 확인한다")
    void findsOrganizationByCurrentCodeAndChecksRetiredCode() {
        Organization organization = organizations.save(organization("R2T3V4"));
        organizations.saveRetiredCode(organization.changeCode(new CompanyCode("R2T3V5"), NOW.plusSeconds(60)));
        organizations.save(organization);
        organizationRepository.flush();

        assertThat(organizations.findByCode(new CompanyCode("R2T3V5")))
                .hasValueSatisfying(found -> assertThat(found.id()).isEqualTo(organization.id()));
        assertThat(organizations.findByCode(new CompanyCode("R2T3V4"))).isEmpty();
        assertThat(organizations.existsRetiredCode(new CompanyCode("R2T3V4"))).isTrue();
        assertThat(organizations.existsRetiredCode(new CompanyCode("R2T3V5"))).isFalse();
        assertThat(organizations.existsRetiredCode(new CompanyCode("R2T3V6"))).isFalse();
    }

    @Test
    @DisplayName("회사 코드를 바꿔 저장하면 새 코드로 복원되고 이전 코드는 폐기 코드로 남는다")
    void savesChangedCodeAndRetiredCode() {
        Organization organization = organizations.save(organization("S2T3V4"));
        OrganizationId organizationId = organization.id().orElseThrow();
        Instant changedAt = NOW.plusSeconds(60);

        organizations.saveRetiredCode(organization.changeCode(new CompanyCode("W5X6Y7"), changedAt));
        organizations.save(organization);
        organizationRepository.flush();
        entityManager.clear();

        Organization restored = organizations.findById(organizationId).orElseThrow();
        assertThat(restored.code()).isEqualTo(new CompanyCode("W5X6Y7"));
        assertThat(restored.name()).isEqualTo(new OrganizationName("오르빗 설비"));
        assertThat(retiredCodeRepository.findAll()).singleElement().satisfies(retired -> assertThat(retired.toDomain())
                .isEqualTo(new RetiredCompanyCode(organizationId, new CompanyCode("S2T3V4"), changedAt)));
    }

    @Test
    @DisplayName("같은 코드를 두 번 폐기 코드로 남길 수 없다")
    void rejectsDuplicateRetiredCodeAtDatabase() {
        OrganizationId organizationId =
                organizations.save(organization("Z8A9B0")).id().orElseThrow();
        RetiredCompanyCode retired = new RetiredCompanyCode(organizationId, new CompanyCode("C1D2E3"), NOW);
        organizations.saveRetiredCode(retired);

        assertThatThrownBy(() -> {
                    organizations.saveRetiredCode(retired);
                    retiredCodeRepository.flush();
                })
                .isInstanceOf(DataIntegrityViolationException.class);
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
    @DisplayName("저장된 발주사의 발주사명·업종을 바꿔 저장하고 회사 코드·생성 시각은 그대로 둔다")
    void updatesExistingOrganization() {
        Organization organization = organizations.save(organization("P1M2N3"));
        OrganizationId organizationId = organization.id().orElseThrow();
        organizationRepository.flush();
        Timestamp createdAt = jdbcTemplate.queryForObject(
                "select created_at from companies where id = ?", Timestamp.class, organizationId.value());

        organization.changeInfo(new OrganizationName("새 발주사"), Industry.HVAC);
        organizations.save(organization);
        organizationRepository.flush();
        entityManager.clear();

        Organization restored = organizations.findById(organizationId).orElseThrow();
        assertThat(restored.name()).isEqualTo(new OrganizationName("새 발주사"));
        assertThat(restored.industry()).isEqualTo(Industry.HVAC);
        assertThat(restored.code()).isEqualTo(new CompanyCode("P1M2N3"));
        assertThat(jdbcTemplate.queryForObject(
                        "select created_at from companies where id = ?", Timestamp.class, organizationId.value()))
                .isEqualTo(createdAt);
    }

    @Test
    @DisplayName("식별자로 발주사를 찾고, 없으면 비어 있다")
    void findsOrganizationById() {
        OrganizationId organizationId =
                organizations.save(organization("B2C3D4")).id().orElseThrow();

        assertThat(organizations.findById(organizationId))
                .hasValueSatisfying(found -> assertThat(found.code()).isEqualTo(new CompanyCode("B2C3D4")));
        assertThat(organizations.findById(new OrganizationId(organizationId.value() + 1_000)))
                .isEmpty();
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
    @DisplayName("계정의 그 발주사 활성 소속만 찾고 그 소속이 있는지 확인한다")
    void findsActiveMembershipOfAccountInOrganization() {
        OrganizationId organizationId =
                organizations.save(organization("E5F6G7")).id().orElseThrow();
        OrganizationId otherId = organizations.save(organization("H8J9K0")).id().orElseThrow();
        memberships.save(Membership.founder(organizationId, new AccountId(7L), NOW));
        MembershipId deactivated = staffMembership(organizationId, 9L);
        jdbcTemplate.update("update company_memberships set status = 'DEACTIVATED' where id = ?", deactivated.value());

        assertThat(memberships.findActive(organizationId, new AccountId(7L))).isPresent();
        assertThat(memberships.findActive(otherId, new AccountId(7L))).isEmpty();
        assertThat(memberships.findActive(organizationId, new AccountId(8L))).isEmpty();
        assertThat(memberships.existsActive(organizationId, new AccountId(7L))).isTrue();
        assertThat(memberships.existsActive(otherId, new AccountId(7L))).isFalse();
        assertThat(memberships.existsActive(organizationId, new AccountId(8L))).isFalse();
        assertThat(memberships.findActive(organizationId, new AccountId(9L))).isEmpty();
        assertThat(memberships.existsActive(organizationId, new AccountId(9L))).isFalse();
    }

    @Test
    @DisplayName("저장된 직원 소속을 다시 저장하면 총관리자 표시와 수정 시각을 바꾸고 가입·생성 시각은 그대로 둔다")
    void updatesOwnerMarkOfStoredMembership() {
        OrganizationId organizationId =
                organizations.save(organization("M1N2P3")).id().orElseThrow();
        MembershipId staffId = staffMembership(organizationId, 8L);
        Membership staff = memberships.findById(staffId).orElseThrow();

        Membership saved = memberships.save(staff.designateAsOwner());
        membershipRepository.flush();

        assertThat(saved.isOwner()).isTrue();
        assertThat(jdbcTemplate.queryForMap(
                        "select is_owner, joined_at, created_at, updated_at from company_memberships where id = ?",
                        staffId.value()))
                .containsEntry("is_owner", true)
                .containsEntry("joined_at", Timestamp.from(NOW))
                .containsEntry("created_at", Timestamp.from(NOW))
                .hasEntrySatisfying("updated_at", updatedAt -> assertThat((Timestamp) updatedAt)
                        .isAfter(Timestamp.from(NOW)));
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
                        + " (company_id, member_id, is_owner, status,"
                        + " joined_at, status_changed_at, created_at, updated_at)"
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
        return Organization.create(new OrganizationName("오르빗 설비"), Industry.PLUMBING, new CompanyCode(code));
    }
}

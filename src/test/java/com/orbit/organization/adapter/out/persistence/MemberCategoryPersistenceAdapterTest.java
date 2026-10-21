package com.orbit.organization.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.orbit.organization.application.port.out.DuplicateCategoryNameException;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.CategoryColor;
import com.orbit.organization.domain.CategoryName;
import com.orbit.organization.domain.MemberCategory;
import com.orbit.organization.domain.MemberCategoryId;
import com.orbit.organization.domain.MemberCategoryKind;
import com.orbit.organization.domain.Membership;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.support.PersistenceAdapterTestSupport;

@DisplayName("직원·기사 유형 저장")
class MemberCategoryPersistenceAdapterTest extends PersistenceAdapterTestSupport {

    private static final Instant NOW = Instant.parse("2026-10-09T00:00:00.123456Z");
    private static final Instant LATER = Instant.parse("2026-10-10T00:00:00.654321Z");
    private static final OrganizationId ORGANIZATION = new OrganizationId(100L);
    private static final OrganizationId OTHER_ORGANIZATION = new OrganizationId(200L);

    @Autowired
    private SpringDataStaffCategoryRepository staffRepository;

    @Autowired
    private SpringDataTechnicianCategoryRepository technicianRepository;

    @Autowired
    private SpringDataMembershipRepository membershipRepository;

    @Autowired
    private SpringDataTechnicianRepository technicianContractRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MemberCategoryPersistenceAdapter categories;
    private MembershipPersistenceAdapter memberships;
    private TechnicianPersistenceAdapter technicians;

    @BeforeEach
    void setUp() {
        categories = new MemberCategoryPersistenceAdapter(staffRepository, technicianRepository);
        memberships = new MembershipPersistenceAdapter(membershipRepository);
        technicians = new TechnicianPersistenceAdapter(technicianContractRepository);
    }

    @Test
    @DisplayName("직원 유형과 기사 유형을 각자의 테이블에 저장하고 같은 값으로 복원한다")
    void savesEachKindInOwnTable() {
        MemberCategory staff = categories.save(category(ORGANIZATION, MemberCategoryKind.STAFF, "정규직"));
        MemberCategory technician = categories.save(category(ORGANIZATION, MemberCategoryKind.TECHNICIAN, "전기 기사"));
        clear();

        assertThat(staffRepository.count()).isEqualTo(1);
        assertThat(technicianRepository.count()).isEqualTo(1);
        MemberCategory restored = categories
                .findById(MemberCategoryKind.TECHNICIAN, technician.id().orElseThrow())
                .orElseThrow();
        assertThat(restored.kind()).isEqualTo(MemberCategoryKind.TECHNICIAN);
        assertThat(restored.organizationId()).isEqualTo(ORGANIZATION);
        assertThat(restored.name()).isEqualTo(new CategoryName("전기 기사"));
        assertThat(restored.color()).isEqualTo(new CategoryColor("#3B82F6"));
        assertThat(restored.isDeleted()).isFalse();
        assertThat(jdbcTemplate.queryForMap(
                        "select created_at, updated_at from company_technician_category where id = ?",
                        technician.id().orElseThrow().value()))
                .allSatisfy((column, value) -> assertThat(value).isNotNull());
        assertThat(categories.findById(MemberCategoryKind.STAFF, staff.id().orElseThrow()))
                .get()
                .extracting(MemberCategory::kind)
                .isEqualTo(MemberCategoryKind.STAFF);
    }

    @Test
    @DisplayName("이름·색상 변경과 소프트 삭제를 저장하고 삭제된 유형도 식별자로 찾는다")
    void savesChangesAndSoftDeletion() {
        MemberCategory saved = categories.save(category(ORGANIZATION, MemberCategoryKind.STAFF, "정규직"));
        saved.change(new CategoryName("계약직"), new CategoryColor("#EF4444"));
        categories.save(saved);
        saved.delete(LATER);
        categories.save(saved);
        clear();

        MemberCategory restored = categories
                .findById(MemberCategoryKind.STAFF, saved.id().orElseThrow())
                .orElseThrow();
        assertThat(restored.name().value()).isEqualTo("계약직");
        assertThat(restored.color().value()).isEqualTo("#EF4444");
        assertThat(restored.isDeleted()).isTrue();
        assertThat(restored.deletedAt()).contains(LATER);
    }

    @Test
    @DisplayName("이름 중복 조회는 같은 발주사·같은 종류의 삭제되지 않은 유형만, 대소문자까지 같은 이름으로 찾는다")
    void findsUndeletedSameNameWithinOrganizationAndKind() {
        MemberCategory deleted = categories.save(category(ORGANIZATION, MemberCategoryKind.STAFF, "AS팀"));
        deleted.delete(LATER);
        categories.save(deleted);
        categories.save(category(ORGANIZATION, MemberCategoryKind.TECHNICIAN, "AS팀"));
        categories.save(category(OTHER_ORGANIZATION, MemberCategoryKind.STAFF, "AS팀"));
        clear();

        assertThat(categories.findUndeletedByName(ORGANIZATION, MemberCategoryKind.STAFF, new CategoryName("AS팀")))
                .isEmpty();

        MemberCategory recreated = categories.save(category(ORGANIZATION, MemberCategoryKind.STAFF, "AS팀"));
        clear();

        assertThat(categories.findUndeletedByName(ORGANIZATION, MemberCategoryKind.STAFF, new CategoryName("AS팀")))
                .get()
                .extracting(MemberCategory::id)
                .isEqualTo(recreated.id());
        assertThat(categories.findUndeletedByName(ORGANIZATION, MemberCategoryKind.STAFF, new CategoryName("as팀")))
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"company_membership_category", "company_technician_category"})
    @DisplayName("DB는 같은 발주사에 삭제되지 않은 같은 이름의 유형을 두 행 두지 않고, 삭제된 행·다른 발주사와는 같은 이름을 허용한다")
    void databaseAllowsOneUndeletedNamePerOrganization(String table) {
        insertRow(table, ORGANIZATION, "중복", true);
        insertRow(table, ORGANIZATION, "중복", true);
        insertRow(table, ORGANIZATION, "중복", false);
        insertRow(table, OTHER_ORGANIZATION, "중복", false);

        assertThatThrownBy(() -> insertRow(table, ORGANIZATION, "중복", false))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("새 유형 저장이 이름 유일 인덱스에 걸리면 저장소 예외로 알린다")
    void reportsDuplicateNameOnInsert() {
        insertRow("company_technician_category", ORGANIZATION, "전기 기사", false);

        assertThatThrownBy(() -> categories.save(category(ORGANIZATION, MemberCategoryKind.TECHNICIAN, "전기 기사")))
                .isInstanceOf(DuplicateCategoryNameException.class);
    }

    @Test
    @DisplayName("이름 변경 저장이 이름 유일 인덱스에 걸리면 저장소 예외로 알린다")
    void reportsDuplicateNameOnRename() {
        insertRow("company_membership_category", ORGANIZATION, "정규직", false);
        MemberCategory renamed = categories.save(category(ORGANIZATION, MemberCategoryKind.STAFF, "계약직"));
        renamed.change(new CategoryName("정규직"), new CategoryColor("#3B82F6"));

        assertThatThrownBy(() -> categories.save(renamed)).isInstanceOf(DuplicateCategoryNameException.class);
    }

    @Test
    @DisplayName("다른 종류의 유형은 같은 이름이어도 인덱스에 걸리지 않는다")
    void allowsSameNameAcrossKinds() {
        categories.save(category(ORGANIZATION, MemberCategoryKind.STAFF, "AS팀"));

        assertThatCode(() -> categories.save(category(ORGANIZATION, MemberCategoryKind.TECHNICIAN, "AS팀")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("목록은 그 발주사·그 종류의 삭제되지 않은 유형을 만든 순서대로 돌려준다")
    void listsUndeletedCategoriesInCreationOrder() {
        categories.save(category(ORGANIZATION, MemberCategoryKind.STAFF, "다유형"));
        categories.save(category(ORGANIZATION, MemberCategoryKind.STAFF, "가유형"));
        MemberCategory deleted = categories.save(category(ORGANIZATION, MemberCategoryKind.STAFF, "나유형"));
        deleted.delete(LATER);
        categories.save(deleted);
        categories.save(category(ORGANIZATION, MemberCategoryKind.TECHNICIAN, "기사유형"));
        categories.save(category(OTHER_ORGANIZATION, MemberCategoryKind.STAFF, "남의유형"));
        clear();

        assertThat(categories.listUndeleted(ORGANIZATION, MemberCategoryKind.STAFF))
                .extracting(category -> category.name().value())
                .containsExactly("다유형", "가유형");
    }

    @Test
    @DisplayName("직원 유형 해제는 그 발주사에서 그 유형이 지정된 소속만 유형 없음으로 바꾼다")
    void unassignsStaffCategoryOnlyFromMatchingMemberships() {
        Long target = membershipWithCategory(ORGANIZATION, 1L, 5L);
        Long otherCategory = membershipWithCategory(ORGANIZATION, 2L, 6L);
        Long otherOrganization = membershipWithCategory(OTHER_ORGANIZATION, 3L, 5L);
        clear();

        int changed = memberships.unassignStaffCategory(ORGANIZATION, new MemberCategoryId(5L), LATER);

        assertThat(changed).isEqualTo(1);
        assertThat(staffCategoryOf(target)).isNull();
        assertThat(updatedAtOf(target)).isEqualTo(LATER);
        assertThat(staffCategoryOf(otherCategory)).isEqualTo(6L);
        assertThat(staffCategoryOf(otherOrganization)).isEqualTo(5L);
    }

    @Test
    @DisplayName("기사 유형 해제는 그 발주사에서 그 유형이 지정된 기사 계약만 상태와 무관하게 유형 없음으로 바꾼다")
    void unassignsTechnicianCategoryOnlyFromMatchingTechnicians() {
        Long target = technicianWithCategory(ORGANIZATION, 1L, 5L, "ACTIVE");
        Long deactivated = technicianWithCategory(ORGANIZATION, 4L, 5L, "DEACTIVATED");
        Long otherCategory = technicianWithCategory(ORGANIZATION, 2L, 6L, "ACTIVE");
        Long otherOrganization = technicianWithCategory(OTHER_ORGANIZATION, 3L, 5L, "ACTIVE");

        int changed = technicians.unassignTechnicianCategory(ORGANIZATION, new MemberCategoryId(5L), LATER);

        assertThat(changed).isEqualTo(2);
        assertThat(technicianCategoryOf(target)).isNull();
        assertThat(technicianCategoryOf(deactivated)).isNull();
        assertThat(jdbcTemplate.queryForMap(
                        "select status, status_changed_at from technician where id = ?", deactivated))
                .containsEntry("status", "DEACTIVATED")
                .containsEntry("status_changed_at", Timestamp.from(NOW));
        assertThat(jdbcTemplate
                        .queryForObject("select updated_at from technician where id = ?", Timestamp.class, target)
                        .toInstant())
                .isEqualTo(LATER);
        assertThat(technicianCategoryOf(otherCategory)).isEqualTo(6L);
        assertThat(technicianCategoryOf(otherOrganization)).isEqualTo(5L);
    }

    /** 기사 계약 도메인이 아직 없어 행을 직접 넣는다. */
    private Long technicianWithCategory(OrganizationId organizationId, long accountId, long categoryId, String status) {
        jdbcTemplate.update(
                "insert into technician (company_id, member_id, company_technician_category_id, status,"
                        + " status_changed_at, joined_at, updated_at, created_at)"
                        + " values (?, ?, ?, ?, ?, ?, ?, current_timestamp)",
                organizationId.value(),
                accountId,
                categoryId,
                status,
                Timestamp.from(NOW),
                Timestamp.from(NOW),
                Timestamp.from(NOW));
        return jdbcTemplate.queryForObject(
                "select id from technician where company_id = ? and member_id = ?",
                Long.class,
                organizationId.value(),
                accountId);
    }

    private Long technicianCategoryOf(Long technicianId) {
        return jdbcTemplate.queryForObject(
                "select company_technician_category_id from technician where id = ?", Long.class, technicianId);
    }

    private Long membershipWithCategory(OrganizationId organizationId, long accountId, long categoryId) {
        Membership saved = memberships.save(Membership.founder(organizationId, new AccountId(accountId), NOW));
        Long id = saved.id().orElseThrow().value();
        entityManager.flush();
        entityManager
                .getEntityManager()
                .createNativeQuery("update company_memberships set company_membership_category_id = ?1 where id = ?2")
                .setParameter(1, categoryId)
                .setParameter(2, id)
                .executeUpdate();
        return id;
    }

    /** 저장소를 거치지 않고 행을 넣어 DB 제약만 확인한다. 실패하면 그 문장만 되돌린다. */
    private void insertRow(String table, OrganizationId organizationId, String name, boolean deleted) {
        jdbcTemplate.update(
                "insert into " + table + " (company_id, name, color, is_deleted, created_at, updated_at, deleted_at)"
                        + " values (?, ?, '#3B82F6', ?, ?, ?, ?)",
                organizationId.value(),
                name,
                deleted,
                Timestamp.from(NOW),
                Timestamp.from(NOW),
                deleted ? Timestamp.from(NOW) : null);
    }

    private Long staffCategoryOf(Long membershipId) {
        return (Long) entityManager
                .getEntityManager()
                .createNativeQuery("select company_membership_category_id from company_memberships where id = ?1")
                .setParameter(1, membershipId)
                .getSingleResult();
    }

    private Instant updatedAtOf(Long membershipId) {
        return entityManager
                .getEntityManager()
                .createQuery("select m.updatedAt from MembershipJpaEntity m where m.id = :id", Instant.class)
                .setParameter("id", membershipId)
                .getSingleResult();
    }

    private void clear() {
        entityManager.flush();
        entityManager.clear();
    }

    private static MemberCategory category(OrganizationId organizationId, MemberCategoryKind kind, String name) {
        return MemberCategory.create(organizationId, kind, new CategoryName(name), new CategoryColor("#3B82F6"));
    }
}

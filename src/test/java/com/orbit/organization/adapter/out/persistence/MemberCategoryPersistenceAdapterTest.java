package com.orbit.organization.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.CategoryColor;
import com.orbit.organization.domain.CategoryName;
import com.orbit.organization.domain.MemberCategory;
import com.orbit.organization.domain.MemberCategoryId;
import com.orbit.organization.domain.MemberCategoryKind;
import com.orbit.organization.domain.Membership;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.support.TestcontainersConfiguration;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("직원·기사 유형 저장")
class MemberCategoryPersistenceAdapterTest {

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
    private TestEntityManager entityManager;

    private MemberCategoryPersistenceAdapter categories;
    private MembershipPersistenceAdapter memberships;

    @BeforeEach
    void setUp() {
        categories = new MemberCategoryPersistenceAdapter(staffRepository, technicianRepository);
        memberships = new MembershipPersistenceAdapter(membershipRepository);
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
        assertThat(restored.createdAt()).isEqualTo(NOW);
        assertThat(restored.isDeleted()).isFalse();
        assertThat(categories.findById(MemberCategoryKind.STAFF, staff.id().orElseThrow()))
                .get()
                .extracting(MemberCategory::kind)
                .isEqualTo(MemberCategoryKind.STAFF);
    }

    @Test
    @DisplayName("이름·색상 변경과 소프트 삭제를 저장하고 삭제된 유형도 식별자로 찾는다")
    void savesChangesAndSoftDeletion() {
        MemberCategory saved = categories.save(category(ORGANIZATION, MemberCategoryKind.STAFF, "정규직"));
        saved.change(new CategoryName("계약직"), new CategoryColor("#EF4444"), LATER);
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
        assertThat(restored.updatedAt()).isEqualTo(LATER);
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

    @Test
    @DisplayName("목록은 그 발주사·그 종류의 삭제되지 않은 유형을 만든 순서대로 돌려준다")
    void listsUndeletedCategoriesInCreationOrder() {
        categories.save(category(ORGANIZATION, MemberCategoryKind.STAFF, "다유형", LATER));
        categories.save(category(ORGANIZATION, MemberCategoryKind.STAFF, "가유형", NOW));
        MemberCategory deleted = categories.save(category(ORGANIZATION, MemberCategoryKind.STAFF, "나유형", NOW));
        deleted.delete(LATER);
        categories.save(deleted);
        categories.save(category(ORGANIZATION, MemberCategoryKind.TECHNICIAN, "기사유형", NOW));
        categories.save(category(OTHER_ORGANIZATION, MemberCategoryKind.STAFF, "남의유형", NOW));
        clear();

        assertThat(categories.listUndeleted(ORGANIZATION, MemberCategoryKind.STAFF))
                .extracting(category -> category.name().value())
                .containsExactly("가유형", "다유형");
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
        return category(organizationId, kind, name, NOW);
    }

    private static MemberCategory category(
            OrganizationId organizationId, MemberCategoryKind kind, String name, Instant createdAt) {
        return MemberCategory.create(
                organizationId, kind, new CategoryName(name), new CategoryColor("#3B82F6"), createdAt);
    }
}

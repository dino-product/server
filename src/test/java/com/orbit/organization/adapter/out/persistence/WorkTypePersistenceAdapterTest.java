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
import org.testcontainers.junit.jupiter.Testcontainers;

import com.orbit.organization.application.port.out.DuplicateCategoryNameException;
import com.orbit.organization.domain.CategoryColor;
import com.orbit.organization.domain.CategoryName;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.WorkType;
import com.orbit.support.PersistenceTestConfiguration;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PersistenceTestConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("작업 유형 저장")
class WorkTypePersistenceAdapterTest {

    private static final Instant NOW = Instant.parse("2026-10-09T00:00:00.123456Z");
    private static final Instant LATER = Instant.parse("2026-10-10T00:00:00.654321Z");
    private static final OrganizationId ORGANIZATION = new OrganizationId(100L);
    private static final OrganizationId OTHER_ORGANIZATION = new OrganizationId(200L);

    @Autowired
    private SpringDataWorkTypeRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private WorkTypePersistenceAdapter workTypes;

    @BeforeEach
    void setUp() {
        workTypes = new WorkTypePersistenceAdapter(repository);
    }

    @Test
    @DisplayName("작업 유형을 저장하고 같은 값으로 복원한다")
    void savesAndRestores() {
        WorkType saved = workTypes.save(workType(ORGANIZATION, "설치"));
        clear();

        WorkType restored = workTypes.findById(saved.id().orElseThrow()).orElseThrow();
        assertThat(restored.organizationId()).isEqualTo(ORGANIZATION);
        assertThat(restored.name()).isEqualTo(new CategoryName("설치"));
        assertThat(restored.color()).isEqualTo(new CategoryColor("#3B82F6"));
        assertThat(restored.isDeleted()).isFalse();
        assertThat(jdbcTemplate.queryForMap(
                        "select created_at, updated_at from work_type where id = ?",
                        saved.id().orElseThrow().value()))
                .allSatisfy((column, value) -> assertThat(value).isNotNull());
    }

    @Test
    @DisplayName("이름·색상 변경과 소프트 삭제를 저장하고 삭제된 작업 유형도 이름·색상과 함께 식별자로 찾는다")
    void savesChangesAndSoftDeletionKeepingLabel() {
        WorkType saved = workTypes.save(workType(ORGANIZATION, "설치"));
        saved.change(new CategoryName("수리"), new CategoryColor("#EF4444"));
        workTypes.save(saved);
        saved.delete(LATER);
        workTypes.save(saved);
        clear();

        WorkType restored = workTypes.findById(saved.id().orElseThrow()).orElseThrow();
        assertThat(restored.name().value()).isEqualTo("수리");
        assertThat(restored.color().value()).isEqualTo("#EF4444");
        assertThat(restored.isDeleted()).isTrue();
        assertThat(restored.deletedAt()).contains(LATER);
    }

    @Test
    @DisplayName("이름 중복 조회는 같은 발주사의 삭제되지 않은 작업 유형만, 대소문자까지 같은 이름으로 찾는다")
    void findsUndeletedSameNameWithinOrganization() {
        WorkType deleted = workTypes.save(workType(ORGANIZATION, "AS점검"));
        deleted.delete(LATER);
        workTypes.save(deleted);
        workTypes.save(workType(OTHER_ORGANIZATION, "AS점검"));
        clear();

        assertThat(workTypes.findUndeletedByName(ORGANIZATION, new CategoryName("AS점검")))
                .isEmpty();

        WorkType recreated = workTypes.save(workType(ORGANIZATION, "AS점검"));
        clear();

        assertThat(workTypes.findUndeletedByName(ORGANIZATION, new CategoryName("AS점검")))
                .get()
                .extracting(WorkType::id)
                .isEqualTo(recreated.id());
        assertThat(workTypes.findUndeletedByName(ORGANIZATION, new CategoryName("as점검")))
                .isEmpty();
    }

    @Test
    @DisplayName("DB는 같은 발주사에 삭제되지 않은 같은 이름의 작업 유형을 두 행 두지 않고, 삭제된 행·다른 발주사와는 같은 이름을 허용한다")
    void databaseAllowsOneUndeletedNamePerOrganization() {
        insertRow(ORGANIZATION, "중복", true);
        insertRow(ORGANIZATION, "중복", true);
        insertRow(ORGANIZATION, "중복", false);
        insertRow(OTHER_ORGANIZATION, "중복", false);

        assertThatThrownBy(() -> insertRow(ORGANIZATION, "중복", false))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("새 작업 유형 저장이 이름 유일 인덱스에 걸리면 저장소 예외로 알린다")
    void reportsDuplicateNameOnInsert() {
        insertRow(ORGANIZATION, "설치", false);

        assertThatThrownBy(() -> workTypes.save(workType(ORGANIZATION, "설치")))
                .isInstanceOf(DuplicateCategoryNameException.class);
    }

    @Test
    @DisplayName("이름 변경 저장이 이름 유일 인덱스에 걸리면 저장소 예외로 알린다")
    void reportsDuplicateNameOnRename() {
        insertRow(ORGANIZATION, "설치", false);
        WorkType renamed = workTypes.save(workType(ORGANIZATION, "수리"));
        renamed.change(new CategoryName("설치"), new CategoryColor("#3B82F6"));

        assertThatThrownBy(() -> workTypes.save(renamed)).isInstanceOf(DuplicateCategoryNameException.class);
    }

    @Test
    @DisplayName("목록은 그 발주사의 삭제되지 않은 작업 유형을 만든 순서대로 돌려준다")
    void listsUndeletedWorkTypesInCreationOrder() {
        workTypes.save(workType(ORGANIZATION, "점검"));
        workTypes.save(workType(ORGANIZATION, "설치"));
        WorkType deleted = workTypes.save(workType(ORGANIZATION, "수리"));
        deleted.delete(LATER);
        workTypes.save(deleted);
        workTypes.save(workType(OTHER_ORGANIZATION, "기타"));
        clear();

        assertThat(workTypes.listUndeleted(ORGANIZATION))
                .extracting(workType -> workType.name().value())
                .containsExactly("점검", "설치");
    }

    /** 저장소를 거치지 않고 행을 넣어 DB 제약만 확인한다. 실패하면 그 문장만 되돌린다. */
    private void insertRow(OrganizationId organizationId, String name, boolean deleted) {
        jdbcTemplate.update(
                "insert into work_type (company_id, name, color, is_deleted, created_at, updated_at, deleted_at)"
                        + " values (?, ?, '#3B82F6', ?, ?, ?, ?)",
                organizationId.value(),
                name,
                deleted,
                Timestamp.from(NOW),
                Timestamp.from(NOW),
                deleted ? Timestamp.from(NOW) : null);
    }

    private void clear() {
        entityManager.flush();
        entityManager.clear();
    }

    private static WorkType workType(OrganizationId organizationId, String name) {
        return WorkType.create(organizationId, new CategoryName(name), new CategoryColor("#3B82F6"));
    }
}

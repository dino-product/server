package com.orbit.organization.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("작업 유형 도메인")
class WorkTypeTest {

    private static final Instant LATER = Instant.parse("2026-10-10T00:00:00Z");
    private static final Instant LATEST = Instant.parse("2026-10-11T00:00:00Z");
    private static final OrganizationId ORGANIZATION = new OrganizationId(1L);
    private static final CategoryName NAME = new CategoryName("설치");
    private static final CategoryColor COLOR = new CategoryColor("#3B82F6");

    @Test
    @DisplayName("새 작업 유형은 식별자 없이 발주사·이름·색상을 가지며 삭제되지 않은 상태다")
    void createsUndeletedWorkTypeWithoutId() {
        WorkType workType = WorkType.create(ORGANIZATION, NAME, COLOR);

        assertThat(workType.id()).isEmpty();
        assertThat(workType.organizationId()).isEqualTo(ORGANIZATION);
        assertThat(workType.name()).isEqualTo(NAME);
        assertThat(workType.color()).isEqualTo(COLOR);
        assertThat(workType.isDeleted()).isFalse();
        assertThat(workType.deletedAt()).isEmpty();
    }

    @Test
    @DisplayName("이름·색상을 함께 바꾼다")
    void changesNameAndColor() {
        WorkType workType = persisted();

        workType.change(new CategoryName("수리"), new CategoryColor("#EF4444"));

        assertThat(workType.name().value()).isEqualTo("수리");
        assertThat(workType.color().value()).isEqualTo("#EF4444");
        assertThat(workType.organizationId()).isEqualTo(ORGANIZATION);
    }

    @Test
    @DisplayName("삭제는 하드 삭제 없이 삭제 표시와 삭제 시각을 남기고 이름·색상은 그대로 둔다")
    void softDeletesKeepingLabel() {
        WorkType workType = persisted();

        boolean deleted = workType.delete(LATER);

        assertThat(deleted).isTrue();
        assertThat(workType.isDeleted()).isTrue();
        assertThat(workType.deletedAt()).contains(LATER);
        assertThat(workType.name()).isEqualTo(NAME);
        assertThat(workType.color()).isEqualTo(COLOR);
    }

    @Test
    @DisplayName("이미 삭제된 작업 유형을 다시 삭제하면 처음 삭제 시각을 유지하고 바뀐 것이 없다고 알린다")
    void deletingAgainKeepsFirstDeletion() {
        WorkType workType = persisted();
        workType.delete(LATER);

        boolean deletedAgain = workType.delete(LATEST);

        assertThat(deletedAgain).isFalse();
        assertThat(workType.deletedAt()).contains(LATER);
    }

    @Test
    @DisplayName("삭제된 작업 유형은 이름·색상을 바꿀 수 없다")
    void rejectsChangingDeletedWorkType() {
        WorkType workType = persisted();
        workType.delete(LATER);

        assertThatThrownBy(() -> workType.change(new CategoryName("수리"), COLOR))
                .isInstanceOf(IllegalStateException.class);
        assertThat(workType.name()).isEqualTo(NAME);
    }

    @Test
    @DisplayName("복원할 때 삭제 표시와 삭제 시각은 함께 있어야 한다")
    void rejectsInconsistentDeletion() {
        assertThatThrownBy(() -> WorkType.reconstitute(new WorkTypeId(1L), ORGANIZATION, NAME, COLOR, true, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WorkType.reconstitute(new WorkTypeId(1L), ORGANIZATION, NAME, COLOR, false, LATER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("작업 유형 식별자는 양수다")
    void rejectsNonPositiveId() {
        assertThatThrownBy(() -> new WorkTypeId(0L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WorkTypeId(null)).isInstanceOf(IllegalArgumentException.class);
    }

    private static WorkType persisted() {
        return WorkType.reconstitute(new WorkTypeId(1L), ORGANIZATION, NAME, COLOR, false, null);
    }
}

package com.orbit.organization.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("직원·기사 유형 도메인")
class MemberCategoryTest {

    private static final Instant NOW = Instant.parse("2026-10-09T00:00:00Z");
    private static final Instant LATER = Instant.parse("2026-10-10T00:00:00Z");
    private static final Instant LATEST = Instant.parse("2026-10-11T00:00:00Z");
    private static final OrganizationId ORGANIZATION = new OrganizationId(1L);
    private static final CategoryName NAME = new CategoryName("정규직");
    private static final CategoryColor COLOR = new CategoryColor("#3B82F6");

    @Test
    @DisplayName("새 유형은 식별자 없이 발주사·종류·이름·색상을 가지며 삭제되지 않은 상태다")
    void createsUndeletedCategoryWithoutId() {
        MemberCategory category = MemberCategory.create(ORGANIZATION, MemberCategoryKind.STAFF, NAME, COLOR, NOW);

        assertThat(category.id()).isEmpty();
        assertThat(category.organizationId()).isEqualTo(ORGANIZATION);
        assertThat(category.kind()).isEqualTo(MemberCategoryKind.STAFF);
        assertThat(category.name()).isEqualTo(NAME);
        assertThat(category.color()).isEqualTo(COLOR);
        assertThat(category.isDeleted()).isFalse();
        assertThat(category.deletedAt()).isEmpty();
        assertThat(category.createdAt()).isEqualTo(NOW);
        assertThat(category.updatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("이름·색상을 바꾸면 수정 시각이 바뀌고 종류·발주사·생성 시각은 그대로다")
    void changesNameAndColor() {
        MemberCategory category = MemberCategory.create(ORGANIZATION, MemberCategoryKind.TECHNICIAN, NAME, COLOR, NOW);

        category.change(new CategoryName("전기 기사"), new CategoryColor("#EF4444"), LATER);

        assertThat(category.name().value()).isEqualTo("전기 기사");
        assertThat(category.color().value()).isEqualTo("#EF4444");
        assertThat(category.updatedAt()).isEqualTo(LATER);
        assertThat(category.kind()).isEqualTo(MemberCategoryKind.TECHNICIAN);
        assertThat(category.organizationId()).isEqualTo(ORGANIZATION);
        assertThat(category.createdAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("삭제는 하드 삭제 없이 삭제 표시와 삭제 시각을 남긴다")
    void softDeletes() {
        MemberCategory category = persisted();

        boolean deleted = category.delete(LATER);

        assertThat(deleted).isTrue();
        assertThat(category.isDeleted()).isTrue();
        assertThat(category.deletedAt()).contains(LATER);
        assertThat(category.updatedAt()).isEqualTo(LATER);
        assertThat(category.name()).isEqualTo(NAME);
    }

    @Test
    @DisplayName("이미 삭제된 유형을 다시 삭제하면 처음 삭제 시각을 유지하고 바뀐 것이 없다고 알린다")
    void deletingAgainKeepsFirstDeletion() {
        MemberCategory category = persisted();
        category.delete(LATER);

        boolean deletedAgain = category.delete(LATEST);

        assertThat(deletedAgain).isFalse();
        assertThat(category.deletedAt()).contains(LATER);
        assertThat(category.updatedAt()).isEqualTo(LATER);
    }

    @Test
    @DisplayName("삭제된 유형은 이름·색상을 바꿀 수 없다")
    void rejectsChangingDeletedCategory() {
        MemberCategory category = persisted();
        category.delete(LATER);

        assertThatThrownBy(() -> category.change(new CategoryName("새 유형"), COLOR, LATEST))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("수정·삭제 시각은 생성 시각보다 앞설 수 없다")
    void rejectsChangeBeforeCreation() {
        MemberCategory category = persisted();

        assertThatThrownBy(() -> category.change(NAME, COLOR, NOW.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> category.delete(NOW.minusSeconds(1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("삭제 표시와 삭제 시각은 함께 있거나 함께 없어야 한다")
    void rejectsInconsistentDeletion() {
        assertThatThrownBy(() -> MemberCategory.reconstitute(
                        new MemberCategoryId(1L),
                        ORGANIZATION,
                        MemberCategoryKind.STAFF,
                        NAME,
                        COLOR,
                        NOW,
                        NOW,
                        true,
                        null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MemberCategory.reconstitute(
                        new MemberCategoryId(1L),
                        ORGANIZATION,
                        MemberCategoryKind.STAFF,
                        NAME,
                        COLOR,
                        NOW,
                        LATER,
                        false,
                        LATER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 10})
    @DisplayName("유형 이름은 2자 이상 10자 이하다")
    void acceptsNameWithinTwoToTenCharacters(int length) {
        assertThat(new CategoryName("가".repeat(length)).value()).hasSize(length);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 11})
    @DisplayName("2자 미만이거나 10자를 넘는 유형 이름은 거부한다")
    void rejectsNameOutsideTwoToTenCharacters(int length) {
        assertThatThrownBy(() -> new CategoryName("가".repeat(length))).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"   "})
    @DisplayName("비었거나 공백뿐인 유형 이름은 거부한다")
    void rejectsBlankName(String value) {
        assertThatThrownBy(() -> new CategoryName(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("앞뒤 공백을 지운 뒤 길이를 세며, 길이는 문자 수(코드 포인트)다")
    void stripsWhitespaceAndCountsCodePoints() {
        assertThat(new CategoryName("  " + "가".repeat(10) + "　").value()).isEqualTo("가".repeat(10));
        assertThat(new CategoryName("𠀀".repeat(10)).value()).hasSize(20);
        assertThatThrownBy(() -> new CategoryName(" 가 ")).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"설치😀", "설치❤️", "1️⃣설치", "설치™"})
    @DisplayName("이모지가 들어간 유형 이름은 거부한다")
    void rejectsNameContainingEmoji(String value) {
        assertThatThrownBy(() -> new CategoryName(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"설치\0팀", "설치\n팀", "설치\t팀", "설치\u007F팀", "설치\u0085팀"})
    @DisplayName("제어 문자(NUL·줄바꿈·탭 포함)가 들어간 유형 이름은 거부한다")
    void rejectsNameContainingControlCharacter(String value) {
        assertThatThrownBy(() -> new CategoryName(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"설치\u2028팀", "설치\u2029팀", "설치\u200B팀", "설치\u202E팀", "설치\uFEFF팀", "설치\u00AD팀"})
    @DisplayName("줄·문단 구분자와 서식 문자(제로폭·방향 제어 등)가 들어간 유형 이름은 거부한다")
    void rejectsNameContainingSeparatorOrFormatCharacter(String value) {
        assertThatThrownBy(() -> new CategoryName(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("유형 이름은 대소문자를 구분해 같은 이름인지 판단한다")
    void comparesNameCaseSensitively() {
        assertThat(new CategoryName(" AS팀 ")).isEqualTo(new CategoryName("AS팀"));
        assertThat(new CategoryName("as팀")).isNotEqualTo(new CategoryName("AS팀"));
    }

    @Test
    @DisplayName("색상은 프리셋 8색 중 하나이며 소문자로 보내도 대문자 HEX로 저장한다")
    void acceptsPresetColors() {
        assertThat(CategoryColor.PRESETS).hasSize(8).doesNotHaveDuplicates();
        CategoryColor.PRESETS.forEach(
                preset -> assertThat(new CategoryColor(preset).value()).isEqualTo(preset));
        assertThat(new CategoryColor("#3b82f6").value()).isEqualTo("#3B82F6");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "#000000", "3B82F6", "#3B82F", "blue"})
    @DisplayName("프리셋에 없는 색상은 거부한다")
    void rejectsColorOutsidePresets(String value) {
        assertThatThrownBy(() -> new CategoryColor(value)).isInstanceOf(IllegalArgumentException.class);
    }

    private static MemberCategory persisted() {
        return MemberCategory.reconstitute(
                new MemberCategoryId(7L), ORGANIZATION, MemberCategoryKind.STAFF, NAME, COLOR, NOW, NOW, false, null);
    }
}

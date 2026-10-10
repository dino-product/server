package com.orbit.organization.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("발주사 도메인")
class OrganizationTest {

    private static final Instant NOW = Instant.parse("2026-10-06T00:00:00Z");
    private static final Instant LATER = Instant.parse("2026-10-08T00:00:00Z");
    private static final CompanyCode CODE = new CompanyCode("7K2M9X");

    @Test
    @DisplayName("새 발주사는 식별자 없이 발주사명·업종·회사 코드·생성 시각을 가진다")
    void createsOrganizationWithoutId() {
        Organization organization = Organization.create(new OrganizationName("오르빗 설비"), Industry.HVAC, CODE, NOW);

        assertThat(organization.id()).isEmpty();
        assertThat(organization.name().value()).isEqualTo("오르빗 설비");
        assertThat(organization.industry()).isEqualTo(Industry.HVAC);
        assertThat(organization.code()).isEqualTo(CODE);
        assertThat(organization.createdAt()).isEqualTo(NOW);
        assertThat(organization.updatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("저장된 발주사는 식별자와 함께 복원된다")
    void reconstitutesPersistedOrganization() {
        Organization organization = Organization.reconstitute(
                new OrganizationId(1L), new OrganizationName("오르빗 설비"), Industry.OTHER, CODE, NOW, LATER);

        assertThat(organization.id()).contains(new OrganizationId(1L));
        assertThat(organization.updatedAt()).isEqualTo(LATER);
    }

    @Test
    @DisplayName("발주사명·업종을 바꾸면 수정 시각이 바뀌고 회사 코드·생성 시각은 그대로다")
    void changesNameAndIndustry() {
        Organization organization = Organization.create(new OrganizationName("오르빗 설비"), Industry.HVAC, CODE, NOW);

        organization.changeInfo(new OrganizationName("새 발주사"), Industry.PLUMBING, LATER);

        assertThat(organization.name().value()).isEqualTo("새 발주사");
        assertThat(organization.industry()).isEqualTo(Industry.PLUMBING);
        assertThat(organization.updatedAt()).isEqualTo(LATER);
        assertThat(organization.code()).isEqualTo(CODE);
        assertThat(organization.createdAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("수정 시각은 생성 시각보다 앞설 수 없다")
    void rejectsChangeBeforeCreation() {
        Organization organization = Organization.create(new OrganizationName("오르빗 설비"), Industry.HVAC, CODE, NOW);

        assertThatThrownBy(() ->
                        organization.changeInfo(new OrganizationName("새 발주사"), Industry.PLUMBING, NOW.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("회사 코드를 바꾸면 이전 코드를 폐기 코드로 돌려주고 수정 시각이 바뀐다")
    void changesCodeAndRetiresPreviousCode() {
        Organization organization = Organization.reconstitute(
                new OrganizationId(1L), new OrganizationName("오르빗 설비"), Industry.HVAC, CODE, NOW, NOW);

        RetiredCompanyCode retired = organization.changeCode(new CompanyCode("Q4ZT8B"), LATER);

        assertThat(organization.code()).isEqualTo(new CompanyCode("Q4ZT8B"));
        assertThat(organization.updatedAt()).isEqualTo(LATER);
        assertThat(organization.name().value()).isEqualTo("오르빗 설비");
        assertThat(retired).isEqualTo(new RetiredCompanyCode(new OrganizationId(1L), CODE, LATER));
    }

    @Test
    @DisplayName("현재 코드와 같은 코드로는 바꿀 수 없다")
    void rejectsChangeToSameCode() {
        Organization organization = Organization.reconstitute(
                new OrganizationId(1L), new OrganizationName("오르빗 설비"), Industry.HVAC, CODE, NOW, NOW);

        assertThatThrownBy(() -> organization.changeCode(CODE, LATER)).isInstanceOf(IllegalArgumentException.class);
        assertThat(organization.updatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("저장되지 않은 발주사는 폐기 코드를 남길 수 없어 회사 코드를 바꿀 수 없다")
    void rejectsCodeChangeOfUnsavedOrganization() {
        Organization organization = Organization.create(new OrganizationName("오르빗 설비"), Industry.HVAC, CODE, NOW);

        assertThatThrownBy(() -> organization.changeCode(new CompanyCode("Q4ZT8B"), LATER))
                .isInstanceOf(IllegalStateException.class);
        assertThat(organization.code()).isEqualTo(CODE);
    }

    @Test
    @DisplayName("회사 코드 변경 시각은 생성 시각보다 앞설 수 없다")
    void rejectsCodeChangeBeforeCreation() {
        Organization organization = Organization.reconstitute(
                new OrganizationId(1L), new OrganizationName("오르빗 설비"), Industry.HVAC, CODE, LATER, LATER);

        assertThatThrownBy(() -> organization.changeCode(new CompanyCode("Q4ZT8B"), NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(organization.code()).isEqualTo(CODE);
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 30})
    @DisplayName("발주사명은 2자 이상 30자 이하다")
    void acceptsNameWithinTwoToThirtyCharacters(int length) {
        assertThat(new OrganizationName("가".repeat(length)).value()).hasSize(length);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 31})
    @DisplayName("2자 미만이거나 30자를 넘는 발주사명은 거부한다")
    void rejectsNameOutsideTwoToThirtyCharacters(int length) {
        assertThatThrownBy(() -> new OrganizationName("가".repeat(length))).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"   "})
    @DisplayName("비었거나 공백뿐인 발주사명은 거부한다")
    void rejectsBlankName(String value) {
        assertThatThrownBy(() -> new OrganizationName(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("발주사명 길이는 문자 수(코드 포인트)로 센다")
    void countsNameLengthByCodePoints() {
        String supplementaryHanja = "\uD840\uDC00";

        assertThat(new OrganizationName(supplementaryHanja.repeat(30)).value()).hasSize(60);
    }

    @Test
    @DisplayName("앞뒤 공백을 지우고 저장한다")
    void stripsLeadingAndTrailingWhitespace() {
        assertThat(new OrganizationName(" \u3000오르빗 설비\t ").value()).isEqualTo("오르빗 설비");
    }

    @Test
    @DisplayName("앞뒤 공백을 지운 뒤 길이를 검사한다")
    void checksLengthAfterStrippingWhitespace() {
        assertThat(new OrganizationName("  " + "가".repeat(30) + "  ").value()).hasSize(30);
        assertThatThrownBy(() -> new OrganizationName(" 가 ")).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "오르빗\uD83D\uDE00",
                "오르빗\u2600",
                "오르빗\u2764\uFE0F",
                "\uD83C\uDDF0\uD83C\uDDF7오르빗",
                "1\uFE0F\u20E3오르빗",
                "오르빗\u2122"
            })
    @DisplayName("이모지(국기·키캡·이모지 표시 기호 포함)가 들어간 발주사명은 거부한다")
    void rejectsNameContainingEmoji(String value) {
        assertThatThrownBy(() -> new OrganizationName(value)).isInstanceOf(IllegalArgumentException.class);
    }
}

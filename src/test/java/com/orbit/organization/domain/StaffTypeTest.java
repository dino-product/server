package com.orbit.organization.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class StaffTypeTest {
    @ParameterizedTest
    @ValueSource(strings = {"상담", "가나다라마바사아자차"})
    void createsAndRenamesAtLengthBoundaries(String name) {
        var type = StaffType.create(
                new StaffTypeId(1L), new OrganizationId(2L), new PersonnelTypeName(name), new TypeColor(1));
        assertThat(type.name().value()).isEqualTo(name);
        assertThat(type.active()).isTrue();
        assertThat(type.id()).isEqualTo(new StaffTypeId(1L));
        assertThat(type.organizationId()).isEqualTo(new OrganizationId(2L));
        type.rename(new PersonnelTypeName("새유형"));
        type.changeColor(new TypeColor(8));
        assertThat(type.name().value()).isEqualTo("새유형");
        assertThat(type.color().value()).isEqualTo(8);
        type.deactivate();
        assertThat(type.active()).isFalse();
        type.activate();
        assertThat(type.active()).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "가", "가나다라마바사아자차카"})
    void rejectsInvalidNames(String name) {
        assertThatThrownBy(() -> new PersonnelTypeName(name)).isInstanceOf(OrganizationRuleViolation.class);
    }

    @Test
    void normalizesSurroundingWhitespace() {
        assertThat(new PersonnelTypeName("  상담  ").value()).isEqualTo("상담");
    }

    @Test
    void rejectsMissingFieldsAndPreservesExistingValues() {
        var id = new StaffTypeId(1L);
        var organization = new OrganizationId(2L);
        var name = new PersonnelTypeName("상담");
        var color = new TypeColor(1);
        assertThatThrownBy(() -> StaffType.create(null, organization, name, color))
                .isInstanceOf(OrganizationRuleViolation.class);
        assertThatThrownBy(() -> StaffType.create(id, null, name, color)).isInstanceOf(OrganizationRuleViolation.class);
        assertThatThrownBy(() -> StaffType.create(id, organization, null, color))
                .isInstanceOf(OrganizationRuleViolation.class);
        assertThatThrownBy(() -> StaffType.create(id, organization, name, null))
                .isInstanceOf(OrganizationRuleViolation.class);
        var type = StaffType.create(id, organization, name, color);
        assertThatThrownBy(() -> type.rename(null)).isInstanceOf(OrganizationRuleViolation.class);
        assertThatThrownBy(() -> type.changeColor(null)).isInstanceOf(OrganizationRuleViolation.class);
        assertThat(type.name()).isEqualTo(name);
        assertThat(type.color()).isEqualTo(color);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void rejectsInvalidId(Long value) {
        assertThatThrownBy(() -> new StaffTypeId(value)).isInstanceOf(OrganizationRuleViolation.class);
    }
}

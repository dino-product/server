package com.orbit.organization.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class TechnicianTest {
    private static final Instant NOW = Instant.parse("2026-09-28T00:00:00Z");
    private static final OrganizationId ORGANIZATION = new OrganizationId(1L);

    @Test
    void createsActiveRelationshipWithoutTypeAndCanReactivate() {
        var relation = create(null);
        assertThat(relation.id()).isEqualTo(new TechnicianId(1L));
        assertThat(relation.organizationId()).isEqualTo(ORGANIZATION);
        assertThat(relation.authAccountId()).isEqualTo(new AuthAccountId(2L));
        assertThat(relation.contractedAt()).isEqualTo(NOW);
        assertThat(relation.typeId()).isNull();
        assertThat(relation.active()).isTrue();
        relation.deactivate();
        assertThat(relation.active()).isFalse();
        relation.activate();
        assertThat(relation.active()).isTrue();
        assertThat(relation.contractedAt()).isEqualTo(NOW);
    }

    @Test
    void assignsAndClearsOptionalType() {
        var type = type(ORGANIZATION);
        var relation = create(type);
        assertThat(relation.typeId()).isEqualTo(type.id());
        relation.changeType(null);
        assertThat(relation.typeId()).isNull();
        relation.changeType(type);
        assertThat(relation.typeId()).isEqualTo(type.id());
        type.deactivate();
        assertThat(relation.typeId()).isEqualTo(type.id());
        assertThat(relation.active()).isTrue();
    }

    @Test
    void rejectsInactiveAndForeignTypesWithoutChangingExistingType() {
        var original = type(ORGANIZATION);
        var relation = create(original);
        var foreign = type(new OrganizationId(3L));
        var inactive = type(ORGANIZATION);
        inactive.deactivate();
        assertThatThrownBy(() -> create(foreign)).isInstanceOf(OrganizationRuleViolation.class);
        assertThatThrownBy(() -> create(inactive)).isInstanceOf(OrganizationRuleViolation.class);
        assertThatThrownBy(() -> relation.changeType(foreign)).isInstanceOf(OrganizationRuleViolation.class);
        assertThatThrownBy(() -> relation.changeType(inactive)).isInstanceOf(OrganizationRuleViolation.class);
        assertThat(relation.typeId()).isEqualTo(original.id());
    }

    @Test
    void rejectsMissingRequiredFields() {
        var id = new TechnicianId(1L);
        var account = new AuthAccountId(2L);
        assertThatThrownBy(() -> Technician.create(null, ORGANIZATION, account, null, NOW))
                .isInstanceOf(OrganizationRuleViolation.class);
        assertThatThrownBy(() -> Technician.create(id, null, account, null, NOW))
                .isInstanceOf(OrganizationRuleViolation.class);
        assertThatThrownBy(() -> Technician.create(id, ORGANIZATION, null, null, NOW))
                .isInstanceOf(OrganizationRuleViolation.class);
        assertThatThrownBy(() -> Technician.create(id, ORGANIZATION, account, null, null))
                .isInstanceOf(OrganizationRuleViolation.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void rejectsInvalidId(Long value) {
        assertThatThrownBy(() -> new TechnicianId(value)).isInstanceOf(OrganizationRuleViolation.class);
    }

    private Technician create(TechnicianType type) {
        return Technician.create(new TechnicianId(1L), ORGANIZATION, new AuthAccountId(2L), type, NOW);
    }

    private TechnicianType type(OrganizationId organizationId) {
        return TechnicianType.create(
                new TechnicianTypeId(1L), organizationId, new PersonnelTypeName("설비"), new TypeColor(1));
    }
}

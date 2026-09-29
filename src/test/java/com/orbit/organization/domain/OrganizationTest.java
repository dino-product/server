package com.orbit.organization.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class OrganizationTest {
    private static final OrganizationId ID = new OrganizationId(1L);
    private static final Instant NOW = Instant.parse("2026-09-28T00:00:00Z");

    @Test
    void createsWithSingleOwnerAndOptionalIndustry() {
        var owner = member(1L, ID);
        var organization = create(owner);
        assertThat(organization.id()).isEqualTo(ID);
        assertThat(organization.name()).isEqualTo(new OrganizationName("오빗"));
        assertThat(organization.industry()).isNull();
        assertThat(organization.ownerMembershipId()).isEqualTo(owner.id());
        assertThat(organization.isManagedBy(owner)).isTrue();
        assertThat(organization.isManagedBy(member(2L, ID))).isFalse();
        assertThat(organization.isManagedBy(member(1L, new OrganizationId(2L)))).isFalse();
        assertThat(organization.isManagedBy(null)).isFalse();
        owner.deactivate();
        assertThat(organization.isManagedBy(owner)).isFalse();
    }

    @Test
    void reconstitutesStoredDetailsWithoutAnOwnerObject() {
        var name = new OrganizationName("저장된 회사");
        var code = new CompanyCode("C0DE1234");
        var ownerId = new MembershipId(17L);

        var organization = Organization.reconstitute(ID, name, Industry.OTHER, code, ownerId);

        assertThat(organization.id()).isEqualTo(ID);
        assertThat(organization.name()).isEqualTo(name);
        assertThat(organization.industry()).isEqualTo(Industry.OTHER);
        assertThat(organization.code()).isEqualTo(code);
        assertThat(organization.ownerMembershipId()).isEqualTo(ownerId);
        assertThat(Organization.reconstitute(ID, name, null, code, ownerId).industry())
                .isNull();
    }

    @Test
    void rejectsMissingRequiredStoredOrganizationFields() {
        var name = new OrganizationName("저장된 회사");
        var code = new CompanyCode("C0DE1234");
        var ownerId = new MembershipId(17L);

        assertThatThrownBy(() -> Organization.reconstitute(null, name, null, code, ownerId))
                .isInstanceOf(OrganizationRuleViolation.class);
        assertThatThrownBy(() -> Organization.reconstitute(ID, null, null, code, ownerId))
                .isInstanceOf(OrganizationRuleViolation.class);
        assertThatThrownBy(() -> Organization.reconstitute(ID, name, null, null, ownerId))
                .isInstanceOf(OrganizationRuleViolation.class);
        assertThatThrownBy(() -> Organization.reconstitute(ID, name, null, code, null))
                .isInstanceOf(OrganizationRuleViolation.class);
    }

    @ParameterizedTest
    @EnumSource(Industry.class)
    void changesDetailsAndClearsOptionalIndustry(Industry industry) {
        var organization = create(member(1L, ID));
        organization.updateDetails(new OrganizationName("새회사"), industry);
        assertThat(organization.name().value()).isEqualTo("새회사");
        assertThat(organization.industry()).isEqualTo(industry);
        organization.updateDetails(organization.name(), null);
        assertThat(organization.industry()).isNull();
    }

    @Test
    void changesCompanyCodeWithoutChangingOwnership() {
        var owner = member(1L, ID);
        var organization = create(owner);
        organization.changeCode(new CompanyCode("NEWC0DE1"));
        assertThat(organization.code().value()).isEqualTo("NEWC0DE1");
        assertThat(organization.ownerMembershipId()).isEqualTo(owner.id());
    }

    @Test
    void transfersManagementAndAllowsFormerOwnerDeactivation() {
        var owner = member(1L, ID);
        var next = member(2L, ID);
        var organization = create(owner);
        assertThatThrownBy(() -> organization.deactivateMembership(owner))
                .isInstanceOf(OrganizationRuleViolation.class);
        assertThat(owner.active()).isTrue();
        organization.transferOwnership(next);
        assertThat(organization.ownerMembershipId()).isEqualTo(next.id());
        assertThat(organization.isManagedBy(owner)).isFalse();
        assertThat(organization.isManagedBy(next)).isTrue();
        organization.deactivateMembership(owner);
        assertThat(owner.active()).isFalse();
        assertThatThrownBy(() -> organization.deactivateMembership(next)).isInstanceOf(OrganizationRuleViolation.class);
        assertThat(next.active()).isTrue();
    }

    @Test
    void rejectsMissingForeignOrInactiveOwnerWithoutLosingCurrentOwner() {
        var owner = member(1L, ID);
        var organization = create(owner);
        var foreign = member(2L, new OrganizationId(2L));
        var inactive = member(3L, ID);
        inactive.deactivate();
        assertThatThrownBy(() -> create(null)).isInstanceOf(OrganizationRuleViolation.class);
        assertThatThrownBy(() -> create(foreign)).isInstanceOf(OrganizationRuleViolation.class);
        assertThatThrownBy(() -> create(inactive)).isInstanceOf(OrganizationRuleViolation.class);
        assertThatThrownBy(() -> organization.transferOwnership(null)).isInstanceOf(OrganizationRuleViolation.class);
        assertThatThrownBy(() -> organization.transferOwnership(foreign)).isInstanceOf(OrganizationRuleViolation.class);
        assertThatThrownBy(() -> organization.transferOwnership(inactive))
                .isInstanceOf(OrganizationRuleViolation.class);
        assertThatThrownBy(() -> organization.deactivateMembership(foreign))
                .isInstanceOf(OrganizationRuleViolation.class);
        assertThatThrownBy(() -> organization.deactivateMembership(null)).isInstanceOf(OrganizationRuleViolation.class);
        assertThat(organization.ownerMembershipId()).isEqualTo(owner.id());
        assertThat(foreign.active()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 30})
    void acceptsNameLengthBoundaries(int length) {
        assertThat(new OrganizationName("가".repeat(length)).value()).hasSize(length);
    }

    @Test
    void reconstitutesLegacyNameWithoutTrimmingOrApplyingCurrentInputRule() {
        var restored = OrganizationName.reconstitute(" A ");

        assertThat(restored.value()).isEqualTo(" A ");
        assertThat(restored).isEqualTo(OrganizationName.reconstitute(" A "));
        assertThat(restored.hashCode())
                .isEqualTo(OrganizationName.reconstitute(" A ").hashCode());
        assertThatThrownBy(() -> OrganizationName.reconstitute(null)).isInstanceOf(OrganizationRuleViolation.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "가", "가나다라마바사아자차카타파하가나다라마바사아자차카타파하가나다"})
    void rejectsInvalidNames(String name) {
        assertThatThrownBy(() -> new OrganizationName(name)).isInstanceOf(OrganizationRuleViolation.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void rejectsMissingCompanyCode(String code) {
        assertThatThrownBy(() -> new CompanyCode(code)).isInstanceOf(OrganizationRuleViolation.class);
    }

    @Test
    void rejectsMissingFieldsAndPreservesDetailsOnFailure() {
        var owner = member(1L, ID);
        var name = new OrganizationName("오빗");
        var code = new CompanyCode("C0DE1234");
        assertThatThrownBy(() -> Organization.create(null, name, null, code, owner))
                .isInstanceOf(OrganizationRuleViolation.class);
        assertThatThrownBy(() -> Organization.create(ID, null, null, code, owner))
                .isInstanceOf(OrganizationRuleViolation.class);
        assertThatThrownBy(() -> Organization.create(ID, name, null, null, owner))
                .isInstanceOf(OrganizationRuleViolation.class);
        var organization = create(owner);
        assertThatThrownBy(() -> organization.updateDetails(null, Industry.OTHER))
                .isInstanceOf(OrganizationRuleViolation.class);
        assertThatThrownBy(() -> organization.changeCode(null)).isInstanceOf(OrganizationRuleViolation.class);
        assertThat(organization.name()).isEqualTo(name);
        assertThat(organization.industry()).isNull();
        assertThat(organization.code()).isEqualTo(code);
    }

    private Organization create(Membership owner) {
        return Organization.create(ID, new OrganizationName("오빗"), null, new CompanyCode("C0DE1234"), owner);
    }

    private Membership member(Long id, OrganizationId organizationId) {
        return Membership.create(new MembershipId(id), organizationId, new AuthAccountId(id), null, NOW);
    }
}

package com.orbit.organization.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.in.command.dto.UpdateOrganizationDetailsCommand;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.domain.AuthAccountId;
import com.orbit.organization.domain.CompanyCode;
import com.orbit.organization.domain.Industry;
import com.orbit.organization.domain.Membership;
import com.orbit.organization.domain.MembershipId;
import com.orbit.organization.domain.Organization;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.OrganizationName;
import com.orbit.organization.domain.OrganizationRuleViolation;
import com.orbit.shared.error.BusinessException;

class UpdateOrganizationDetailsServiceTest {
    private static final OrganizationId ID = new OrganizationId(11L);
    private static final AuthAccountId ACCOUNT = new AuthAccountId(9L);
    private static final Instant NOW = Instant.parse("2026-09-28T00:00:00Z");

    private OrganizationRepository organizations;
    private MembershipRepository memberships;
    private UpdateOrganizationDetailsService service;

    @BeforeEach
    void setUp() {
        organizations = mock(OrganizationRepository.class);
        memberships = mock(MembershipRepository.class);
        service = new UpdateOrganizationDetailsService(organizations, memberships);
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 30})
    void ownerUpdatesNameAndClearsOptionalIndustryWithoutChangingCodeOrOwnership(int length) {
        var owner = member(1L, ID, true);
        var organization = organization(owner.id());
        when(organizations.findByIdForUpdate(ID)).thenReturn(Optional.of(organization));
        when(memberships.findByOrganizationAndAccount(ID, ACCOUNT)).thenReturn(Optional.of(owner));

        var details = service.update(
                new UpdateOrganizationDetailsCommand(ACCOUNT.value(), ID.value(), "가".repeat(length), null));

        assertThat(details.organizationId()).isEqualTo(ID.value());
        assertThat(details.name()).hasSize(length);
        assertThat(details.industry()).isNull();
        assertThat(organization.code()).isEqualTo(new CompanyCode("C0DE1234"));
        assertThat(organization.ownerMembershipId()).isEqualTo(owner.id());
        verify(organizations).update(organization);
        var order = inOrder(organizations, memberships);
        order.verify(organizations).findByIdForUpdate(ID);
        order.verify(memberships).findByOrganizationAndAccount(ID, ACCOUNT);
    }

    @Test
    void ownerCanSetIndustry() {
        var owner = member(1L, ID, true);
        var organization = organization(owner.id());
        when(organizations.findByIdForUpdate(ID)).thenReturn(Optional.of(organization));
        when(memberships.findByOrganizationAndAccount(ID, ACCOUNT)).thenReturn(Optional.of(owner));

        var result = service.update(
                new UpdateOrganizationDetailsCommand(ACCOUNT.value(), ID.value(), "새 이름", Industry.HVAC));

        assertThat(result.industry()).isEqualTo(Industry.HVAC);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 31})
    void rejectsInvalidNameWithoutUpdatingStoredState(int length) {
        var owner = member(1L, ID, true);
        var organization = organization(owner.id());
        when(organizations.findByIdForUpdate(ID)).thenReturn(Optional.of(organization));
        when(memberships.findByOrganizationAndAccount(ID, ACCOUNT)).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> service.update(
                        new UpdateOrganizationDetailsCommand(ACCOUNT.value(), ID.value(), "가".repeat(length), null)))
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getErrorCode())
                        .isEqualTo(OrganizationErrorCode.INVALID_ORGANIZATION_INPUT));
        assertThat(organization.name().value()).isEqualTo("회사명");
        assertThat(organization.industry()).isEqualTo(Industry.OTHER);
        verify(organizations, org.mockito.Mockito.never()).update(organization);
    }

    @Test
    void rejectsNonOwnerAndMissingOrganizationWithoutChangingState() {
        var organization = organization(new MembershipId(1L));
        when(organizations.findByIdForUpdate(ID)).thenReturn(Optional.of(organization));
        assertForbidden();
        when(memberships.findByOrganizationAndAccount(ID, ACCOUNT)).thenReturn(Optional.of(member(2L, ID, true)));
        assertForbidden();
        when(memberships.findByOrganizationAndAccount(ID, ACCOUNT)).thenReturn(Optional.of(member(1L, ID, false)));
        assertForbidden();
        when(organizations.findByIdForUpdate(ID)).thenReturn(Optional.empty());
        assertForbidden();
        assertThat(organization.name().value()).isEqualTo("회사명");
        verify(organizations, org.mockito.Mockito.never()).update(organization);
    }

    @Test
    void rejectsNonOwnerBeforeValidatingInvalidName() {
        var organization = organization(new MembershipId(1L));
        when(organizations.findByIdForUpdate(ID)).thenReturn(Optional.of(organization));
        when(memberships.findByOrganizationAndAccount(ID, ACCOUNT)).thenReturn(Optional.of(member(2L, ID, true)));

        assertThatThrownBy(() ->
                        service.update(new UpdateOrganizationDetailsCommand(ACCOUNT.value(), ID.value(), "가", null)))
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getErrorCode())
                        .isEqualTo(OrganizationErrorCode.NOT_ORGANIZATION_OWNER));
        assertThat(organization.name().value()).isEqualTo("회사명");
        assertThat(organization.industry()).isEqualTo(Industry.OTHER);
        verify(organizations, org.mockito.Mockito.never()).update(organization);
    }

    @Test
    void invalidIdsFailBeforeLockingAndNullAccountIsProgrammingError() {
        assertThatThrownBy(() -> service.update(new UpdateOrganizationDetailsCommand(0L, ID.value(), "새 이름", null)))
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getErrorCode())
                        .isEqualTo(OrganizationErrorCode.INVALID_ORGANIZATION_INPUT));
        assertThatThrownBy(
                        () -> service.update(new UpdateOrganizationDetailsCommand(ACCOUNT.value(), 0L, "새 이름", null)))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(OrganizationErrorCode.INVALID_ORGANIZATION_INPUT);
                    assertThat(error.getErrorCode().getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
        assertThatThrownBy(() -> service.update(new UpdateOrganizationDetailsCommand(null, ID.value(), "새 이름", null)))
                .isInstanceOf(NullPointerException.class);
        verifyNoInteractions(organizations, memberships);
    }

    @Test
    void doesNotMapPortRuleViolationToInputError() {
        when(organizations.findByIdForUpdate(ID)).thenThrow(new OrganizationRuleViolation("port failure"));
        assertThatThrownBy(() ->
                        service.update(new UpdateOrganizationDetailsCommand(ACCOUNT.value(), ID.value(), "새 이름", null)))
                .isInstanceOf(OrganizationRuleViolation.class);
    }

    private void assertForbidden() {
        assertThatThrownBy(() ->
                        service.update(new UpdateOrganizationDetailsCommand(ACCOUNT.value(), ID.value(), "새 이름", null)))
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getErrorCode())
                        .isEqualTo(OrganizationErrorCode.NOT_ORGANIZATION_OWNER));
    }

    private Organization organization(MembershipId ownerId) {
        return Organization.reconstitute(
                ID, new OrganizationName("회사명"), Industry.OTHER, new CompanyCode("C0DE1234"), ownerId);
    }

    private Membership member(long id, OrganizationId organizationId, boolean active) {
        return Membership.reconstitute(new MembershipId(id), organizationId, ACCOUNT, null, NOW, active);
    }
}

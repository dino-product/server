package com.orbit.organization.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.in.query.dto.GetOrganizationDetailsQuery;
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

class GetOrganizationDetailsServiceTest {
    private static final OrganizationId ID = new OrganizationId(11L);
    private static final AuthAccountId ACCOUNT = new AuthAccountId(9L);
    private static final Instant NOW = Instant.parse("2026-09-28T00:00:00Z");

    private OrganizationRepository organizations;
    private MembershipRepository memberships;
    private GetOrganizationDetailsService service;

    @BeforeEach
    void setUp() {
        organizations = mock(OrganizationRepository.class);
        memberships = mock(MembershipRepository.class);
        service = new GetOrganizationDetailsService(organizations, memberships);
    }

    @Test
    void ownerReadsDetailsWithoutCompanyCode() {
        var owner = member(1L, ID, true);
        var organization = organization(owner.id());
        when(organizations.findById(ID)).thenReturn(Optional.of(organization));
        when(memberships.findByOrganizationAndAccount(ID, ACCOUNT)).thenReturn(Optional.of(owner));

        var details = service.get(new GetOrganizationDetailsQuery(ACCOUNT.value(), ID.value()));

        assertThat(details.organizationId()).isEqualTo(ID.value());
        assertThat(details.name()).isEqualTo("회사명");
        assertThat(details.industry()).isEqualTo(Industry.OTHER);
        assertThat(details.getClass().getRecordComponents())
                .extracting("name")
                .containsExactly("organizationId", "name", "industry");
    }

    @Test
    void nonOwnerAndNonMemberReceiveTheSameForbiddenError() {
        var organization = organization(new MembershipId(1L));
        when(organizations.findById(ID)).thenReturn(Optional.of(organization));
        assertForbidden(new GetOrganizationDetailsQuery(ACCOUNT.value(), ID.value()));

        when(memberships.findByOrganizationAndAccount(ID, ACCOUNT)).thenReturn(Optional.of(member(2L, ID, true)));
        assertForbidden(new GetOrganizationDetailsQuery(ACCOUNT.value(), ID.value()));

        when(memberships.findByOrganizationAndAccount(ID, ACCOUNT)).thenReturn(Optional.of(member(1L, ID, false)));
        assertForbidden(new GetOrganizationDetailsQuery(ACCOUNT.value(), ID.value()));

        when(memberships.findByOrganizationAndAccount(ID, ACCOUNT))
                .thenReturn(Optional.of(member(1L, new OrganizationId(22L), true)));
        assertForbidden(new GetOrganizationDetailsQuery(ACCOUNT.value(), ID.value()));
    }

    @Test
    void missingOrganizationUsesTheSameForbiddenError() {
        assertForbidden(new GetOrganizationDetailsQuery(ACCOUNT.value(), ID.value()));
    }

    @Test
    void invalidIdsAreInputErrorsBeforePortAccessAndMissingAccountIsProgrammingError() {
        assertThatThrownBy(() -> service.get(new GetOrganizationDetailsQuery(0L, ID.value())))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(OrganizationErrorCode.INVALID_ORGANIZATION_INPUT);
                    assertThat(error.getErrorCode().getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
        assertThatThrownBy(() -> service.get(new GetOrganizationDetailsQuery(ACCOUNT.value(), -1L)))
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getErrorCode())
                        .isEqualTo(OrganizationErrorCode.INVALID_ORGANIZATION_INPUT));
        assertThatThrownBy(() -> service.get(new GetOrganizationDetailsQuery(null, ID.value())))
                .isInstanceOf(NullPointerException.class);
        verifyNoInteractions(organizations, memberships);
    }

    @Test
    void doesNotMapPortRuleViolationToInputError() {
        when(organizations.findById(ID)).thenThrow(new OrganizationRuleViolation("port failure"));
        assertThatThrownBy(() -> service.get(new GetOrganizationDetailsQuery(ACCOUNT.value(), ID.value())))
                .isInstanceOf(OrganizationRuleViolation.class);
    }

    private void assertForbidden(GetOrganizationDetailsQuery query) {
        assertThatThrownBy(() -> service.get(query)).isInstanceOfSatisfying(BusinessException.class, error -> {
            assertThat(error.getErrorCode()).isEqualTo(OrganizationErrorCode.NOT_ORGANIZATION_OWNER);
            assertThat(error.getErrorCode().getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        });
    }

    private Organization organization(MembershipId ownerId) {
        return Organization.reconstitute(
                ID, new OrganizationName("회사명"), Industry.OTHER, new CompanyCode("C0DE1234"), ownerId);
    }

    private Membership member(long id, OrganizationId organizationId, boolean active) {
        return Membership.reconstitute(new MembershipId(id), organizationId, ACCOUNT, null, NOW, active);
    }
}

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
import com.orbit.organization.application.port.in.query.dto.GetCompanyCodeQuery;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.domain.AuthAccountId;
import com.orbit.organization.domain.CompanyCode;
import com.orbit.organization.domain.Membership;
import com.orbit.organization.domain.MembershipId;
import com.orbit.organization.domain.Organization;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.OrganizationName;
import com.orbit.organization.domain.OrganizationRuleViolation;
import com.orbit.shared.error.BusinessException;

class GetCompanyCodeServiceTest {
    private static final OrganizationId ID = new OrganizationId(11L);
    private static final AuthAccountId ACCOUNT = new AuthAccountId(9L);
    private static final CompanyCode CODE = new CompanyCode("C0DE1234");
    private static final Instant NOW = Instant.parse("2026-09-28T00:00:00Z");

    private OrganizationRepository organizations;
    private MembershipRepository memberships;
    private GetCompanyCodeService service;

    @BeforeEach
    void setUp() {
        organizations = mock(OrganizationRepository.class);
        memberships = mock(MembershipRepository.class);
        service = new GetCompanyCodeService(organizations, memberships);
    }

    @Test
    void ownerReadsCurrentCompanyCode() {
        var owner = member(1L, ID, true);
        when(organizations.findById(ID)).thenReturn(Optional.of(organization(owner.id())));
        when(memberships.findByOrganizationAndAccount(ID, ACCOUNT)).thenReturn(Optional.of(owner));

        var result = service.get(new GetCompanyCodeQuery(ACCOUNT.value(), ID.value()));

        assertThat(result.organizationId()).isEqualTo(ID.value());
        assertThat(result.code()).isEqualTo(CODE.value());
    }

    @Test
    void missingOrganizationAndUnauthorizedAccountsReceiveTheSameForbiddenError() {
        var query = new GetCompanyCodeQuery(ACCOUNT.value(), ID.value());
        assertForbidden(query);

        when(organizations.findById(ID)).thenReturn(Optional.of(organization(new MembershipId(1L))));
        assertForbidden(query); // 비소속

        when(memberships.findByOrganizationAndAccount(ID, ACCOUNT)).thenReturn(Optional.of(member(2L, ID, true)));
        assertForbidden(query); // 일반 직원

        when(memberships.findByOrganizationAndAccount(ID, ACCOUNT)).thenReturn(Optional.of(member(1L, ID, false)));
        assertForbidden(query); // 비활성 직원

        when(memberships.findByOrganizationAndAccount(ID, ACCOUNT))
                .thenReturn(Optional.of(member(1L, new OrganizationId(22L), true)));
        assertForbidden(query); // 타사 직원
    }

    @Test
    void invalidIdsAreInputErrorsBeforePortAccessAndMissingAccountIsProgrammingError() {
        assertThatThrownBy(() -> service.get(new GetCompanyCodeQuery(0L, ID.value())))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(OrganizationErrorCode.INVALID_ORGANIZATION_INPUT);
                    assertThat(error.getErrorCode().getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
        assertThatThrownBy(() -> service.get(new GetCompanyCodeQuery(ACCOUNT.value(), -1L)))
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getErrorCode())
                        .isEqualTo(OrganizationErrorCode.INVALID_ORGANIZATION_INPUT));
        assertThatThrownBy(() -> service.get(new GetCompanyCodeQuery(null, ID.value())))
                .isInstanceOf(NullPointerException.class);
        verifyNoInteractions(organizations, memberships);
    }

    @Test
    void doesNotMapPortRuleViolationToInputError() {
        when(organizations.findById(ID)).thenThrow(new OrganizationRuleViolation("port failure"));

        assertThatThrownBy(() -> service.get(new GetCompanyCodeQuery(ACCOUNT.value(), ID.value())))
                .isInstanceOf(OrganizationRuleViolation.class);
    }

    private void assertForbidden(GetCompanyCodeQuery query) {
        assertThatThrownBy(() -> service.get(query)).isInstanceOfSatisfying(BusinessException.class, error -> {
            assertThat(error.getErrorCode()).isEqualTo(OrganizationErrorCode.NOT_ORGANIZATION_OWNER);
            assertThat(error.getErrorCode().getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        });
    }

    private Organization organization(MembershipId ownerId) {
        return Organization.reconstitute(ID, new OrganizationName("회사명"), null, CODE, ownerId);
    }

    private Membership member(long id, OrganizationId organizationId, boolean active) {
        return Membership.reconstitute(new MembershipId(id), organizationId, ACCOUNT, null, NOW, active);
    }
}

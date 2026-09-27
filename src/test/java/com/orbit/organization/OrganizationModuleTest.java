package com.orbit.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.in.command.CreateOrganizationUseCase;
import com.orbit.organization.application.port.in.command.dto.CreateOrganizationCommand;
import com.orbit.organization.application.port.out.CompanyCodeGenerator;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationIdentityPort;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.domain.AuthAccountId;
import com.orbit.organization.domain.CompanyCode;
import com.orbit.organization.domain.MembershipId;
import com.orbit.organization.domain.Organization;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.shared.error.BusinessException;
import com.orbit.support.TestcontainersConfiguration;

@ApplicationModuleTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class OrganizationModuleTest {
    @Autowired
    private CreateOrganizationUseCase create;

    @MockitoSpyBean
    private OrganizationRepository organizations;

    @Autowired
    private MembershipRepository memberships;

    @MockitoSpyBean
    private OrganizationIdentityPort identities;

    @MockitoSpyBean
    private CompanyCodeGenerator codes;

    @Test
    void createsOrganizationMembershipAndOwnerInOneCommittedTransaction() {
        var created = create.create(new CreateOrganizationCommand(301L, "모듈 회사", null));

        var organization = organizations
                .findById(new OrganizationId(created.organizationId()))
                .orElseThrow();
        var membership = memberships
                .findByOrganizationAndAccount(organization.id(), new AuthAccountId(301L))
                .orElseThrow();
        assertThat(organization.ownerMembershipId()).isEqualTo(membership.id());
        assertThat(organization.isManagedBy(membership)).isTrue();
        assertThat(organization.code().value()).isEqualTo(created.code()).matches("[0-9A-HJKMNP-TV-Z]{8}");
    }

    @Test
    void rollsBackOrganizationWhenMembershipInsertFails() {
        var existing = create.create(new CreateOrganizationCommand(302L, "기존 회사", null));
        var attemptedOrganizationId = identities.nextOrganizationId();
        var organizationInserted = new AtomicBoolean();
        doReturn(attemptedOrganizationId).when(identities).nextOrganizationId();
        doReturn(new MembershipId(existing.membershipId())).when(identities).nextMembershipId();
        doAnswer(invocation -> {
                    invocation.callRealMethod();
                    organizations.flush();
                    organizationInserted.set(true);
                    return null;
                })
                .when(organizations)
                .save(any(Organization.class));

        var failure = catchThrowable(() -> create.create(new CreateOrganizationCommand(303L, "새 회사", null)));

        assertThat(organizationInserted).isTrue();
        var root = rootCause(failure);
        assertThat(root).isInstanceOf(SQLException.class);
        assertThat(((SQLException) root).getSQLState()).isEqualTo("23505");
        assertThat(root.getMessage()).contains("membership_pkey");
        assertThat(organizations.findById(attemptedOrganizationId)).isEmpty();
        assertThat(memberships.findByOrganizationAndAccount(attemptedOrganizationId, new AuthAccountId(303L)))
                .isEmpty();
    }

    @Test
    void mapsDatabaseCodeConflictToRetryableErrorAndRollsBack() {
        var existing = create.create(new CreateOrganizationCommand(304L, "기존 회사", null));
        var attemptedOrganizationId = identities.nextOrganizationId();
        var duplicateCode = new CompanyCode(existing.code());
        doReturn(attemptedOrganizationId).when(identities).nextOrganizationId();
        doReturn(duplicateCode).when(codes).generate();
        doReturn(false).when(organizations).existsByCode(duplicateCode);

        assertThatThrownBy(() -> create.create(new CreateOrganizationCommand(305L, "새 회사", null)))
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getErrorCode())
                        .isEqualTo(OrganizationErrorCode.COMPANY_CODE_CONFLICT));

        assertThat(organizations.findById(attemptedOrganizationId)).isEmpty();
        assertThat(memberships.findByOrganizationAndAccount(attemptedOrganizationId, new AuthAccountId(305L)))
                .isEmpty();
    }

    private Throwable rootCause(Throwable failure) {
        assertThat(failure).isNotNull();
        var cause = failure;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }
}

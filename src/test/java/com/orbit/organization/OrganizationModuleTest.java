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
import com.orbit.organization.application.port.in.command.ActivateStaffTypeUseCase;
import com.orbit.organization.application.port.in.command.ActivateTechnicianTypeUseCase;
import com.orbit.organization.application.port.in.command.CreateOrganizationUseCase;
import com.orbit.organization.application.port.in.command.CreateStaffTypeUseCase;
import com.orbit.organization.application.port.in.command.CreateTechnicianTypeUseCase;
import com.orbit.organization.application.port.in.command.DeactivateStaffTypeUseCase;
import com.orbit.organization.application.port.in.command.DeactivateTechnicianTypeUseCase;
import com.orbit.organization.application.port.in.command.DeleteStaffTypeUseCase;
import com.orbit.organization.application.port.in.command.DeleteTechnicianTypeUseCase;
import com.orbit.organization.application.port.in.command.UpdateOrganizationDetailsUseCase;
import com.orbit.organization.application.port.in.command.UpdateStaffTypeUseCase;
import com.orbit.organization.application.port.in.command.UpdateTechnicianTypeUseCase;
import com.orbit.organization.application.port.in.command.dto.ActivateStaffTypeCommand;
import com.orbit.organization.application.port.in.command.dto.ActivateTechnicianTypeCommand;
import com.orbit.organization.application.port.in.command.dto.CreateOrganizationCommand;
import com.orbit.organization.application.port.in.command.dto.CreateStaffTypeCommand;
import com.orbit.organization.application.port.in.command.dto.CreateTechnicianTypeCommand;
import com.orbit.organization.application.port.in.command.dto.DeactivateStaffTypeCommand;
import com.orbit.organization.application.port.in.command.dto.DeactivateTechnicianTypeCommand;
import com.orbit.organization.application.port.in.command.dto.DeleteStaffTypeCommand;
import com.orbit.organization.application.port.in.command.dto.DeleteTechnicianTypeCommand;
import com.orbit.organization.application.port.in.command.dto.UpdateOrganizationDetailsCommand;
import com.orbit.organization.application.port.in.command.dto.UpdateStaffTypeCommand;
import com.orbit.organization.application.port.in.command.dto.UpdateTechnicianTypeCommand;
import com.orbit.organization.application.port.in.query.GetOrganizationDetailsUseCase;
import com.orbit.organization.application.port.in.query.GetStaffTypesUseCase;
import com.orbit.organization.application.port.in.query.GetTechnicianTypesUseCase;
import com.orbit.organization.application.port.in.query.dto.GetOrganizationDetailsQuery;
import com.orbit.organization.application.port.in.query.dto.GetStaffTypesQuery;
import com.orbit.organization.application.port.in.query.dto.GetTechnicianTypesQuery;
import com.orbit.organization.application.port.out.CompanyCodeGenerator;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationIdentityPort;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.application.port.out.StaffTypeRepository;
import com.orbit.organization.application.port.out.StaffTypeUsagePort;
import com.orbit.organization.application.port.out.TechnicianTypeRepository;
import com.orbit.organization.application.port.out.TechnicianTypeUsagePort;
import com.orbit.organization.domain.AuthAccountId;
import com.orbit.organization.domain.CompanyCode;
import com.orbit.organization.domain.Industry;
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

    @Autowired
    private GetOrganizationDetailsUseCase details;

    @Autowired
    private UpdateOrganizationDetailsUseCase update;

    @Autowired
    private GetStaffTypesUseCase listStaffTypes;

    @Autowired
    private CreateStaffTypeUseCase createStaffType;

    @Autowired
    private UpdateStaffTypeUseCase updateStaffType;

    @Autowired
    private ActivateStaffTypeUseCase activateStaffType;

    @Autowired
    private DeactivateStaffTypeUseCase deactivateStaffType;

    @Autowired
    private DeleteStaffTypeUseCase deleteStaffType;

    @Autowired
    private GetTechnicianTypesUseCase listTechnicianTypes;

    @Autowired
    private CreateTechnicianTypeUseCase createTechnicianType;

    @Autowired
    private UpdateTechnicianTypeUseCase updateTechnicianType;

    @Autowired
    private ActivateTechnicianTypeUseCase activateTechnicianType;

    @Autowired
    private DeactivateTechnicianTypeUseCase deactivateTechnicianType;

    @Autowired
    private DeleteTechnicianTypeUseCase deleteTechnicianType;

    @MockitoSpyBean
    private OrganizationRepository organizations;

    @Autowired
    private MembershipRepository memberships;

    @Autowired
    private StaffTypeRepository staffTypes;

    @Autowired
    private StaffTypeUsagePort staffTypeUsage;

    @Autowired
    private TechnicianTypeRepository technicianTypes;

    @Autowired
    private TechnicianTypeUsagePort technicianTypeUsage;

    @Test
    void assemblesStaffTypePersistencePorts() {
        assertThat(staffTypes).isNotNull();
        assertThat(staffTypeUsage).isNotNull();
    }

    @Test
    void assemblesTechnicianTypePersistenceAndTemporaryUsagePorts() {
        assertThat(technicianTypes).isNotNull();
        assertThat(technicianTypeUsage).isNotNull();
    }

    @Test
    void ownerCreatesUpdatesAndListsCommittedStaffTypes() {
        var organization = create.create(new CreateOrganizationCommand(309L, "유형 관리 회사", null));

        var created =
                createStaffType.create(new CreateStaffTypeCommand(309L, organization.organizationId(), " 상담 ", 2));
        var updated = updateStaffType.update(
                new UpdateStaffTypeCommand(309L, organization.organizationId(), created.id(), "팀장", 7));
        var listed = listStaffTypes.get(new GetStaffTypesQuery(309L, organization.organizationId()));

        assertThat(created.name()).isEqualTo("상담");
        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(updated.name()).isEqualTo("팀장");
        assertThat(updated.color()).isEqualTo(7);
        assertThat(listed).containsExactly(updated);
    }

    @Test
    void ownerChangesTypeStateDeletesUnusedTypeAndReusesName() {
        var organization = create.create(new CreateOrganizationCommand(310L, "유형 상태 회사", null));
        var type = createStaffType.create(new CreateStaffTypeCommand(310L, organization.organizationId(), "상담", 2));

        var inactive = deactivateStaffType.deactivate(
                new DeactivateStaffTypeCommand(310L, organization.organizationId(), type.id()));
        assertThat(inactive.active()).isFalse();
        assertThat(listStaffTypes.get(new GetStaffTypesQuery(310L, organization.organizationId())))
                .containsExactly(inactive);

        var active = activateStaffType.activate(
                new ActivateStaffTypeCommand(310L, organization.organizationId(), type.id()));
        assertThat(active.active()).isTrue();
        deleteStaffType.delete(new DeleteStaffTypeCommand(310L, organization.organizationId(), type.id()));
        assertThat(listStaffTypes.get(new GetStaffTypesQuery(310L, organization.organizationId())))
                .isEmpty();

        var recreated =
                createStaffType.create(new CreateStaffTypeCommand(310L, organization.organizationId(), "상담", 3));
        assertThat(recreated.id()).isNotEqualTo(type.id());
        assertThat(recreated.name()).isEqualTo("상담");
    }

    @Test
    void ownerManagesTechnicianTypesThroughCommittedTransactions() {
        var organization = create.create(new CreateOrganizationCommand(311L, "기사 유형 회사", null));
        var organizationId = organization.organizationId();
        var created = createTechnicianType.create(new CreateTechnicianTypeCommand(311L, organizationId, " 전기 ", 2));
        var updated = updateTechnicianType.update(
                new UpdateTechnicianTypeCommand(311L, organizationId, created.id(), "냉방", 7));
        assertThat(listTechnicianTypes.get(new GetTechnicianTypesQuery(311L, organizationId)))
                .containsExactly(updated);

        var inactive = deactivateTechnicianType.deactivate(
                new DeactivateTechnicianTypeCommand(311L, organizationId, created.id()));
        assertThat(inactive.active()).isFalse();
        assertThat(listTechnicianTypes.get(new GetTechnicianTypesQuery(311L, organizationId)))
                .containsExactly(inactive);
        var active =
                activateTechnicianType.activate(new ActivateTechnicianTypeCommand(311L, organizationId, created.id()));
        assertThat(active.active()).isTrue();

        deleteTechnicianType.delete(new DeleteTechnicianTypeCommand(311L, organizationId, created.id()));
        assertThat(listTechnicianTypes.get(new GetTechnicianTypesQuery(311L, organizationId)))
                .isEmpty();
        var recreated = createTechnicianType.create(new CreateTechnicianTypeCommand(311L, organizationId, "냉방", 3));
        assertThat(recreated.id()).isNotEqualTo(created.id());
        assertThat(recreated.name()).isEqualTo("냉방");
    }

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
    void ownerReadsCommittedDetailsAndOtherAccountCannot() {
        var created = create.create(new CreateOrganizationCommand(306L, "조회 회사", null));

        var result = details.get(new GetOrganizationDetailsQuery(306L, created.organizationId()));

        assertThat(result.organizationId()).isEqualTo(created.organizationId());
        assertThat(result.name()).isEqualTo("조회 회사");
        assertThat(result.industry()).isNull();
        assertThatThrownBy(() -> details.get(new GetOrganizationDetailsQuery(307L, created.organizationId())))
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getErrorCode())
                        .isEqualTo(OrganizationErrorCode.NOT_ORGANIZATION_OWNER));
    }

    @Test
    void ownerUpdatesDetailsAndSeesCommittedValuesWithoutChangingCodeOrOwner() {
        var created = create.create(new CreateOrganizationCommand(308L, "기존 회사", Industry.OTHER));
        var ownerId = new MembershipId(created.membershipId());

        var changed =
                update.update(new UpdateOrganizationDetailsCommand(308L, created.organizationId(), "변경 회사", null));
        var reread = details.get(new GetOrganizationDetailsQuery(308L, created.organizationId()));
        var organization = organizations
                .findById(new OrganizationId(created.organizationId()))
                .orElseThrow();

        assertThat(changed).isEqualTo(reread);
        assertThat(reread.name()).isEqualTo("변경 회사");
        assertThat(reread.industry()).isNull();
        assertThat(organization.code().value()).isEqualTo(created.code());
        assertThat(organization.ownerMembershipId()).isEqualTo(ownerId);
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

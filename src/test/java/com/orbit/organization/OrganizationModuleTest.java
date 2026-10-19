package com.orbit.organization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.orbit.organization.application.port.in.command.CreateOrganizationUseCase;
import com.orbit.organization.application.port.in.command.CreateWorkTypeUseCase;
import com.orbit.organization.application.port.in.command.dto.CreateOrganizationCommand;
import com.orbit.organization.application.port.in.command.dto.CreateWorkTypeCommand;
import com.orbit.organization.application.port.in.command.dto.CreatedOrganizationInfo;
import com.orbit.organization.application.port.in.query.dto.WorkTypeInfo;
import com.orbit.organization.domain.Industry;
import com.orbit.support.TestcontainersConfiguration;

@ApplicationModuleTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class OrganizationModuleTest {

    // schedule이 구현하는 요구 인터페이스. 독립 모듈 조립에는 구현이 없어 대체한다.
    @MockitoBean
    private WorkTypeRelease workTypeRelease;

    @Autowired
    private CreateOrganizationUseCase useCase;

    @Autowired
    private OrganizationMemberLookup memberLookup;

    @Autowired
    private TechnicianContractLookup technicianContractLookup;

    @Autowired
    private CreateWorkTypeUseCase createWorkTypeUseCase;

    @Autowired
    private WorkTypeLookup workTypeLookup;

    @Autowired
    private MemberDirectory memberDirectory;

    @Test
    void createsOrganizationWithinStandaloneModule() {
        CreatedOrganizationInfo created =
                useCase.create(new CreateOrganizationCommand(7L, "모듈 발주사", Industry.APPLIANCE_SERVICE));

        assertThat(created.organizationId()).isPositive();
        assertThat(created.companyCode()).hasSize(6);
    }

    @Test
    void publicLookupFindsFounderAsActiveOwner() {
        CreatedOrganizationInfo created =
                useCase.create(new CreateOrganizationCommand(8L, "조회 발주사", Industry.APPLIANCE_SERVICE));

        assertThat(memberLookup.findActiveMember(8L, created.organizationId()))
                .hasValueSatisfying(member -> assertThat(member).isInstanceOfSatisfying(StaffMember.class, staff -> {
                    assertThat(staff.organizationId()).isEqualTo(created.organizationId());
                    assertThat(staff.owner()).isTrue();
                }));
        assertThat(memberLookup.findActiveMember(9L, created.organizationId())).isEmpty();
    }

    @Test
    void publicTechnicianContractLookupFindsNoContractForStaffOnlyAccount() {
        useCase.create(new CreateOrganizationCommand(10L, "기사 계약 조회 발주사", Industry.APPLIANCE_SERVICE));

        assertThat(technicianContractLookup.listByAccount(10L)).isEmpty();
    }

    @Test
    void publicTechnicianContractLookupDeniesUnknownTechnician() {
        CreatedOrganizationInfo created =
                useCase.create(new CreateOrganizationCommand(11L, "기사 계약 확인 발주사", Industry.APPLIANCE_SERVICE));

        assertThat(technicianContractLookup.existsActive(created.organizationId(), 999_999L))
                .isFalse();
    }

    @Test
    void publicWorkTypeLookupSelectsAndLabelsCreatedWorkType() {
        CreatedOrganizationInfo created =
                useCase.create(new CreateOrganizationCommand(12L, "작업 유형 발주사", Industry.APPLIANCE_SERVICE));
        WorkTypeInfo workType =
                createWorkTypeUseCase.create(new CreateWorkTypeCommand(12L, created.organizationId(), "설치", "#3B82F6"));

        assertThat(workTypeLookup.isSelectable(created.organizationId(), workType.workTypeId()))
                .isTrue();
        assertThat(workTypeLookup.findLabels(created.organizationId(), Set.of(workType.workTypeId())))
                .containsExactly(new WorkTypeLabel(workType.workTypeId(), "설치", "#3B82F6", false));
    }

    @Test
    void publicMemberDirectoryFindsFounderAsOwnerWithoutCategory() {
        CreatedOrganizationInfo created =
                useCase.create(new CreateOrganizationCommand(13L, "구성원 표시 발주사", Industry.APPLIANCE_SERVICE));
        StaffMember founder = (StaffMember)
                memberLookup.findActiveMember(13L, created.organizationId()).orElseThrow();

        assertThat(memberDirectory.findStaff(created.organizationId(), Set.of(founder.membershipId())))
                .containsExactly(new StaffDirectoryEntry(founder.membershipId(), 13L, true, null));
        assertThat(memberDirectory.listActiveTechnicians(created.organizationId()))
                .isEmpty();
    }
}

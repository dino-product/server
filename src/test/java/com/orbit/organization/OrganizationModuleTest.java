package com.orbit.organization;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.orbit.organization.application.port.in.command.CreateOrganizationUseCase;
import com.orbit.organization.application.port.in.command.dto.CreateOrganizationCommand;
import com.orbit.organization.application.port.in.command.dto.CreatedOrganizationInfo;
import com.orbit.organization.domain.Industry;
import com.orbit.support.SharedContainers;

@ApplicationModuleTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class OrganizationModuleTest implements SharedContainers {

    @Autowired
    private CreateOrganizationUseCase useCase;

    @Autowired
    private OrganizationMemberLookup memberLookup;

    @Autowired
    private TechnicianContractLookup technicianContractLookup;

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
}

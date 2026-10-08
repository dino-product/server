package com.orbit.organization;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.orbit.organization.application.port.in.command.CreateOrganizationUseCase;
import com.orbit.organization.application.port.in.command.dto.CreateOrganizationCommand;
import com.orbit.organization.application.port.in.command.dto.CreatedOrganizationInfo;
import com.orbit.organization.domain.Industry;
import com.orbit.support.TestcontainersConfiguration;

@ApplicationModuleTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class OrganizationModuleTest {

    @Autowired
    private CreateOrganizationUseCase useCase;

    @Autowired
    private OrganizationMemberLookup memberLookup;

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
}

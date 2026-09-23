package com.orbit.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.support.TestcontainersConfiguration;

@ApplicationModuleTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class ScheduleModuleTest {

    private static final OrganizationId ORGANIZATION_ID = new OrganizationId(100L);

    @Autowired
    private WorkRepository workRepository;

    @Autowired
    private LoadActorPort loadActorPort;

    @Test
    void assembledActorPortDeniesEveryAccountUntilOrganizationIsWired() {
        assertThat(loadActorPort.findActiveActor(1L, ORGANIZATION_ID)).isEmpty();
    }

    @Test
    void assembledWorkRepositoryStoresAndFindsWork() {
        WorkId id = workRepository
                .save(Work.register(ORGANIZATION_ID, "모듈 작업", new MembershipId(1L), null, null, null))
                .id()
                .orElseThrow();

        assertThat(workRepository.findById(id))
                .hasValueSatisfying(work -> assertThat(work.name()).isEqualTo("모듈 작업"));
    }
}

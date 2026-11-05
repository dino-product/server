package com.orbit.schedule.adapter.in.web;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.TechnicianId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkSchedule;

/** 기사 앱의 내 일정·응답 필요·지금 수행 가능 작업 조회 API를 공통 응답·보안·영속성과 함께 확인한다. 준비물은 {@link ScheduleApiTestSupport}를 따른다. */
@DisplayName("기사 일정 조회 API")
class MyScheduleApiIntegrationTest extends ScheduleApiTestSupport {

    private static final Instant REGISTERED_AT = Instant.parse("2020-01-01T00:00:00Z");
    private static final String MY_SCHEDULE = "/api/v1/organizations/{organizationId}/my-schedule";

    @Test
    @DisplayName("내 일정은 구간과 겹치는 본인 담당 확정 작업(응답 전 제외)을 배정 순번·고객명과 함께 시작 시각 순으로 준다")
    void listsOwnScheduleInPeriod() throws Exception {
        Fixture fixture = fixture();
        Instant day = Instant.now().plus(Duration.ofDays(2));
        long later = save(accepted(fixture, "오후 작업", day.plus(Duration.ofHours(4))));
        long earlier = save(accepted(fixture, "오전 작업", day));
        save(assigned(fixture, "응답 전 작업", day.plus(Duration.ofHours(2))));
        save(assigned(fixture, "다음 주 작업", day.plus(Duration.ofDays(7))));

        mockMvc.perform(get(MY_SCHEDULE, fixture.organization().id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(fixture.technician()))
                        .param("from", day.minus(Duration.ofHours(1)).toString())
                        .param("to", day.plus(Duration.ofDays(1)).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.works[*].workId").value(contains((int) earlier, (int) later)))
                .andExpect(jsonPath("$.result.works[0].status").value("ACCEPTED"))
                .andExpect(jsonPath("$.result.works[0].assignmentNumber").value(1))
                .andExpect(jsonPath("$.result.works[0].customerName").doesNotExist())
                .andExpect(jsonPath("$.result.works[0].delayed").value(false));
    }

    @Test
    @DisplayName("응답 필요는 날짜와 관계없이 본인에게 배정돼 응답을 기다리는 작업만 준다")
    void listsPendingWorksRegardlessOfDate() throws Exception {
        Fixture fixture = fixture();
        long soon = save(assigned(fixture, "곧", Instant.now().plus(Duration.ofDays(1))));
        long farAway = save(assigned(fixture, "두 달 뒤", Instant.now().plus(Duration.ofDays(60))));
        save(accepted(fixture, "수락함", Instant.now().plus(Duration.ofDays(1))));

        perform(fixture.technician(), fixture.organization().id(), "/pending")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.works[*].workId").value(contains((int) soon, (int) farAway)))
                .andExpect(jsonPath("$.result.works[*].status").value(contains("PENDING_ACCEPT", "PENDING_ACCEPT")));
    }

    @Test
    @DisplayName("지금 수행 가능은 시작 가능 시간 안의 작업전 작업과 어제 시작한 작업중 작업을 주고, 아직 이른 작업은 뺀다")
    void listsActionableWorks() throws Exception {
        Fixture fixture = fixture();
        long startable = save(accepted(fixture, "곧 시작", Instant.now().plus(Duration.ofMinutes(5))));
        save(accepted(fixture, "한 시간 뒤", Instant.now().plus(Duration.ofHours(1))));
        Work yesterday = accepted(fixture, "어제 시작", Instant.now().minus(Duration.ofDays(1)));
        yesterday.start(Instant.now().minus(Duration.ofHours(20)));
        long inProgress = save(yesterday);

        perform(fixture.technician(), fixture.organization().id(), "/actionable")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.works[*].workId").value(contains((int) inProgress, (int) startable)))
                .andExpect(jsonPath("$.result.works[0].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.result.works[0].delayed").value(true));
    }

    @Test
    @DisplayName("관리자는 403 SCHEDULE-005, 구성원이 아니면 403 SCHEDULE-004, 내 일정의 구간이 없으면 400 SCHEDULE-003, 토큰이 없으면 401이다")
    void rejectsManagerNonMemberMissingPeriodAndAnonymous() throws Exception {
        Fixture fixture = fixture();

        for (String path : new String[] {"/pending", "/actionable"}) {
            perform(fixture.organization().owner(), fixture.organization().id(), path)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-005"));
            perform(account(), fixture.organization().id(), path)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-004"));
            mockMvc.perform(get(MY_SCHEDULE + path, fixture.organization().id()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("COMMON-401"));
        }
        perform(fixture.technician(), fixture.organization().id(), "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCHEDULE-003"));
        perform(fixture.technician(), fixture.organization().id(), "/pending")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.works", hasSize(0)));
    }

    /** 발주사와 그 발주사의 활성 기사 계약 하나. */
    private record Fixture(Organization organization, long technician, long technicianId) {}

    private Fixture fixture() throws Exception {
        Organization organization = organization();
        long technician = account();
        technicianContract(organization.id(), technician);
        return new Fixture(organization, technician, technicianIdOf(organization.id(), technician));
    }

    private ResultActions perform(long requester, long organizationId, String path) throws Exception {
        return mockMvc.perform(
                get(MY_SCHEDULE + path, organizationId).header(HttpHeaders.AUTHORIZATION, bearer(requester)));
    }

    /** 그 기사에게 start부터 2시간 일정으로 배정해 수락을 기다리는 작업. 배정 시각은 지금과 시작 중 이른 쪽보다 하루 앞선다. */
    private static Work assigned(Fixture fixture, String name, Instant start) {
        Work work = Work.register(
                new OrganizationId(fixture.organization().id()),
                name,
                new MembershipId(fixture.organization().ownerMembership()),
                REGISTERED_AT,
                null,
                null,
                null);
        Instant assignedAt = (start.isBefore(Instant.now()) ? start : Instant.now()).minus(Duration.ofDays(1));
        work.assign(
                new WorkSchedule(new TechnicianId(fixture.technicianId()), start, Duration.ofHours(2)),
                assignedAt,
                new MembershipId(fixture.organization().ownerMembership()));
        return work;
    }

    /** {@link #assigned}를 배정 1분 뒤에 수락한 작업. */
    private static Work accepted(Fixture fixture, String name, Instant start) {
        Work work = assigned(fixture, name, start);
        work.accept(work.assignmentHistory().getLast().assignedAt().plusSeconds(60));
        return work;
    }
}

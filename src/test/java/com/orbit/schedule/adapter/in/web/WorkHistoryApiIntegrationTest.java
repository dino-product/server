package com.orbit.schedule.adapter.in.web;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.orbit.schedule.domain.CompletionReport;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.TechnicianId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkSchedule;

/** 기사 작업 이력 API를 공통 응답·보안·영속성과 함께 확인한다. 준비물은 {@link ScheduleApiTestSupport}를 따른다. */
@DisplayName("기사 작업 이력 API")
class WorkHistoryApiIntegrationTest extends ScheduleApiTestSupport {

    private static final Instant REGISTERED_AT = Instant.parse("2020-01-01T00:00:00Z");
    private static final String HISTORY = "/api/v1/organizations/{organizationId}/work-history";
    private static final Instant FROM = Instant.parse("2026-09-30T15:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-31T15:00:00Z");

    @Test
    @DisplayName("기사 본인은 구간 안에서 본인이 완료한 작업과 통계를 받고, 누적 완료는 구간 밖 완료까지 센다")
    void technicianReadsOwnCompletedWorksWithTotal() throws Exception {
        Fixture fixture = fixture();
        long inPeriod = save(completed(fixture, "이번 달 완료", Instant.parse("2026-10-10T01:00:00Z")));
        save(completed(fixture, "지난달 완료", Instant.parse("2026-09-10T01:00:00Z")));
        save(accepted(fixture, "이번 달 작업전", Instant.parse("2026-10-20T01:00:00Z")));

        history(fixture.technician(), fixture.organization().id(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.technicianId").value(fixture.technicianId()))
                .andExpect(jsonPath("$.result.works", hasSize(1)))
                .andExpect(jsonPath("$.result.works[0].workId").value(inPeriod))
                .andExpect(jsonPath("$.result.works[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.result.works[0].current").value(true))
                .andExpect(jsonPath("$.result.works[0].result").value("ACCEPTED"))
                .andExpect(jsonPath("$.result.statistics.assigned").value(1))
                .andExpect(jsonPath("$.result.statistics.completed").value(1))
                .andExpect(jsonPath("$.result.statistics.rejected").value(0))
                .andExpect(jsonPath("$.result.totalCompleted").value(2));
    }

    @Test
    @DisplayName("관리자는 기사를 지정해 지금 상태와 관계없이 구간의 작업을 받고, 기사를 비우면 400 SCHEDULE-003이다")
    void managerReadsGivenTechnician() throws Exception {
        Fixture fixture = fixture();
        save(completed(fixture, "이번 달 완료", Instant.parse("2026-10-10T01:00:00Z")));
        save(accepted(fixture, "이번 달 작업전", Instant.parse("2026-10-20T01:00:00Z")));

        history(fixture.organization().owner(), fixture.organization().id(), fixture.technicianId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.works", hasSize(2)))
                .andExpect(jsonPath("$.result.works[0].status").value("ACCEPTED"))
                .andExpect(jsonPath("$.result.statistics.assigned").value(2))
                .andExpect(jsonPath("$.result.totalCompleted").value(1));
        history(fixture.organization().owner(), fixture.organization().id(), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCHEDULE-003"));
    }

    @Test
    @DisplayName("기사가 다른 기사를 지정하면 403 SCHEDULE-005, 구성원이 아니면 403 SCHEDULE-004, 31일을 넘는 구간은 400 SCHEDULE-003이다")
    void rejectsOtherTechnicianNonMemberAndLongPeriod() throws Exception {
        Fixture fixture = fixture();
        long other = account();
        technicianContract(fixture.organization().id(), other);

        history(
                        fixture.technician(),
                        fixture.organization().id(),
                        technicianIdOf(fixture.organization().id(), other))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SCHEDULE-005"));
        history(account(), fixture.organization().id(), null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SCHEDULE-004"));
        mockMvc.perform(authorized(fixture.technician(), fixture.organization().id())
                        .param("from", FROM.toString())
                        .param("to", FROM.plus(Duration.ofDays(32)).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCHEDULE-003"));
    }

    @Test
    @DisplayName("상태 조건은 목록만 거르고, 완료한 작업에는 실제 시작·완료 시각을 담는다. 없는 상태 값은 400 COMMON-400, 양수가 아닌 작업유형은 400 SCHEDULE-003이다")
    void filtersByStatusAndShowsActualTimes() throws Exception {
        Fixture fixture = fixture();
        Instant start = Instant.parse("2026-10-10T01:00:00Z");
        long done = save(completed(fixture, "완료", start));
        save(accepted(fixture, "작업전", Instant.parse("2026-10-20T01:00:00Z")));

        mockMvc.perform(authorized(
                                fixture.organization().owner(),
                                fixture.organization().id())
                        .param("technicianId", Long.toString(fixture.technicianId()))
                        .param("from", FROM.toString())
                        .param("to", TO.toString())
                        .param("statuses", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.works", hasSize(1)))
                .andExpect(jsonPath("$.result.works[0].workId").value(done))
                .andExpect(jsonPath("$.result.works[0].startedAt").value(start.toString()))
                .andExpect(jsonPath("$.result.works[0].completedAt")
                        .value(start.plus(Duration.ofHours(2)).toString()))
                .andExpect(jsonPath("$.result.statistics.assigned").value(2));
        mockMvc.perform(authorized(
                                fixture.organization().owner(),
                                fixture.organization().id())
                        .param("technicianId", Long.toString(fixture.technicianId()))
                        .param("from", FROM.toString())
                        .param("to", TO.toString())
                        .param("statuses", "IN_PROGRESS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
        mockMvc.perform(authorized(
                                fixture.organization().owner(),
                                fixture.organization().id())
                        .param("technicianId", Long.toString(fixture.technicianId()))
                        .param("from", FROM.toString())
                        .param("to", TO.toString())
                        .param("workTypeIds", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCHEDULE-003"));
    }

    @Test
    @DisplayName("구간을 빠뜨리면 형식 오류가 아니라 서비스가 확인해 400 SCHEDULE-003이다")
    void rejectsMissingPeriodInService() throws Exception {
        Fixture fixture = fixture();

        mockMvc.perform(authorized(fixture.technician(), fixture.organization().id()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCHEDULE-003"));
    }

    @Test
    @DisplayName("형식이 틀린 시각·기사 식별자는 400 COMMON-400, 토큰이 없으면 401이다")
    void rejectsMalformedQueryAndAnonymous() throws Exception {
        Fixture fixture = fixture();

        mockMvc.perform(authorized(fixture.technician(), fixture.organization().id())
                        .param("from", "2026-10-01")
                        .param("to", TO.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
        mockMvc.perform(authorized(fixture.technician(), fixture.organization().id())
                        .param("technicianId", "abc")
                        .param("from", FROM.toString())
                        .param("to", TO.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
        mockMvc.perform(get(HISTORY, fixture.organization().id())
                        .param("from", FROM.toString())
                        .param("to", TO.toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON-401"));
    }

    /** 발주사와 그 발주사의 활성 기사 계약 하나. */
    private record Fixture(Organization organization, long technician, long technicianId) {}

    private Fixture fixture() throws Exception {
        Organization organization = organization();
        long technician = account();
        technicianContract(organization.id(), technician);
        return new Fixture(organization, technician, technicianIdOf(organization.id(), technician));
    }

    private ResultActions history(long requester, long organizationId, Long technicianId) throws Exception {
        MockHttpServletRequestBuilder request = authorized(requester, organizationId)
                .param("from", FROM.toString())
                .param("to", TO.toString());
        if (technicianId != null) {
            request.param("technicianId", technicianId.toString());
        }
        return mockMvc.perform(request);
    }

    private MockHttpServletRequestBuilder authorized(long requester, long organizationId) throws Exception {
        return get(HISTORY, organizationId).header(HttpHeaders.AUTHORIZATION, bearer(requester));
    }

    /** 그 기사에게 start부터 2시간 일정으로 배정해 하루 전에 수락한 작업. */
    private static Work accepted(Fixture fixture, String name, Instant start) {
        Work work = Work.register(
                new OrganizationId(fixture.organization().id()),
                name,
                new MembershipId(fixture.organization().ownerMembership()),
                REGISTERED_AT,
                null,
                null,
                null);
        work.assign(
                new WorkSchedule(new TechnicianId(fixture.technicianId()), start, Duration.ofHours(2)),
                start.minus(Duration.ofDays(1)),
                new MembershipId(fixture.organization().ownerMembership()));
        work.accept(start.minus(Duration.ofHours(12)));
        return work;
    }

    /** {@link #accepted}를 예정 시각에 시작해 예정 종료에 완료한 작업. */
    private static Work completed(Fixture fixture, String name, Instant start) {
        Work work = accepted(fixture, name, start);
        work.start(start);
        work.submitCompletionReport(
                new CompletionReport(List.of(), List.of(), null, null, null, null), start.plus(Duration.ofHours(2)));
        return work;
    }
}

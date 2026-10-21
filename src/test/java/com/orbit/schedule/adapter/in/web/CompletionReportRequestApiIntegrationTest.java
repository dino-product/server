package com.orbit.schedule.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.ResultActions;

import com.orbit.schedule.CompletionReportRequested;
import com.orbit.schedule.domain.CompletionReportRequest;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.TechnicianId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkSchedule;

/** 완료보고 요청 API를 공통 응답·보안·트랜잭션·영속성·이벤트 발행과 함께 확인한다. 준비물은 {@link ScheduleApiTestSupport}를 따른다. */
@DisplayName("완료보고 요청 API")
@RecordApplicationEvents
class CompletionReportRequestApiIntegrationTest extends ScheduleApiTestSupport {

    private static final Instant REGISTERED_AT = Instant.parse("2020-01-01T00:00:00Z");
    private static final String REQUESTS =
            "/api/v1/organizations/{organizationId}/works/{workId}/completion-report-requests";
    private static final String WORK = "/api/v1/organizations/{organizationId}/works/{workId}";

    @Autowired
    private ApplicationEvents events;

    @Test
    @DisplayName("등록자가 아닌 직원도 예정 종료가 지난 작업중 작업에 요청하면 200과 요청 시각을 돌려주고, 기록을 남겨 담당 기사에게 알릴 요청을 발행한다")
    void requestsCompletionReport() throws Exception {
        Organization organization = organization();
        long staff = account();
        staffMembership(organization.id(), staff);
        long technician = account();
        technicianContract(organization.id(), technician);
        long technicianId = technicianIdOf(organization.id(), technician);
        long workId = save(inProgress(organization, technicianId, Instant.now().minus(Duration.ofHours(3))));

        request(staff, organization.id(), workId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result.requestedAt").exists());

        Work saved = stored(organization, workId);
        assertThat(saved.completionReportRequests())
                .singleElement()
                .extracting(CompletionReportRequest::requestedBy)
                .isEqualTo(new MembershipId(staffMembershipOf(organization.id(), staff)));
        assertThat(events.stream(CompletionReportRequested.class))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.workId()).isEqualTo(workId);
                    assertThat(event.organizationId()).isEqualTo(organization.id());
                    assertThat(event.technicianId()).isEqualTo(technicianId);
                    assertThat(event.workName()).isEqualTo("늦은 작업");
                    assertThat(event.occurredAt())
                            .isEqualTo(
                                    saved.completionReportRequests().getFirst().requestedAt());
                });

        detail(organization.owner(), organization.id(), workId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.completionReportRequests", hasSize(1)))
                .andExpect(jsonPath("$.result.completionReportRequests[0].requestedBy")
                        .value(staffMembershipOf(organization.id(), staff)))
                .andExpect(jsonPath("$.result.completionReportRequestable").value(false));
        detail(technician, organization.id(), workId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.completionReportRequests", empty()))
                .andExpect(jsonPath("$.result.completionReportRequestable").value(false));
    }

    @Test
    @DisplayName("요청 직후 다시 요청하면 429 SCHEDULE-019이고 기록·발행이 늘지 않는다")
    void rejectsRepeatedRequestWithinInterval() throws Exception {
        Organization organization = organization();
        long workId = save(inProgress(organization, 1L, Instant.now().minus(Duration.ofHours(3))));

        request(organization.owner(), organization.id(), workId).andExpect(status().isOk());
        request(organization.owner(), organization.id(), workId)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("SCHEDULE-019"));

        assertThat(stored(organization, workId).completionReportRequests()).hasSize(1);
        assertThat(events.stream(CompletionReportRequested.class)).hasSize(1);
    }

    @Test
    @DisplayName("예정 종료 전이거나 작업중이 아닌 작업은 409 SCHEDULE-002이고 기록·발행하지 않는다")
    void rejectsWorkNotOverdueOrNotInProgress() throws Exception {
        Organization organization = organization();
        long notOverdue = save(inProgress(organization, 1L, Instant.now().minus(Duration.ofMinutes(30))));
        Work accepted = assigned(organization, 1L, Instant.now().minus(Duration.ofHours(3)));
        accepted.accept(Instant.now().minus(Duration.ofHours(2)));
        long acceptedId = save(accepted);

        for (long workId : new long[] {notOverdue, acceptedId}) {
            request(organization.owner(), organization.id(), workId)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-002"));
            assertThat(stored(organization, workId).completionReportRequests()).isEmpty();
        }
        assertThat(events.stream(CompletionReportRequested.class)).isEmpty();
    }

    @Test
    @DisplayName("기사는 403 SCHEDULE-005, 구성원이 아니면 403 SCHEDULE-004이고 기록하지 않는다")
    void rejectsTechnicianAndNonMember() throws Exception {
        Organization organization = organization();
        long technician = account();
        technicianContract(organization.id(), technician);
        long workId = save(inProgress(
                organization,
                technicianIdOf(organization.id(), technician),
                Instant.now().minus(Duration.ofHours(3))));

        request(technician, organization.id(), workId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SCHEDULE-005"));
        request(account(), organization.id(), workId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SCHEDULE-004"));
        assertThat(stored(organization, workId).completionReportRequests()).isEmpty();
        assertThat(events.stream(CompletionReportRequested.class)).isEmpty();
    }

    @Test
    @DisplayName("다른 조직의 작업은 404 SCHEDULE-001, 숫자가 아닌 식별자는 400 COMMON-400, 토큰이 없으면 401이다")
    void rejectsOtherOrganizationMalformedIdAndAnonymous() throws Exception {
        Organization organization = organization();
        Organization other = organization();
        long otherWorkId = save(inProgress(other, 1L, Instant.now().minus(Duration.ofHours(3))));

        request(organization.owner(), organization.id(), otherWorkId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCHEDULE-001"));
        mockMvc.perform(post(REQUESTS, organization.id(), "abc")
                        .header(HttpHeaders.AUTHORIZATION, bearer(organization.owner())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
        mockMvc.perform(post(REQUESTS, other.id(), otherWorkId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON-401"));
        assertThat(stored(other, otherWorkId).completionReportRequests()).isEmpty();
    }

    private ResultActions request(long requester, long organizationId, long workId) throws Exception {
        return mockMvc.perform(
                post(REQUESTS, organizationId, workId).header(HttpHeaders.AUTHORIZATION, bearer(requester)));
    }

    private ResultActions detail(long requester, long organizationId, long workId) throws Exception {
        return mockMvc.perform(get(WORK, organizationId, workId).header(HttpHeaders.AUTHORIZATION, bearer(requester)));
    }

    /** 주어진 기사에게 start부터 2시간 일정으로 배정하고 수락·시작한 작업. start가 3시간 전이면 예정 종료가 1시간 지났다. */
    private static Work inProgress(Organization organization, long technicianId, Instant start) {
        Work work = assigned(organization, technicianId, start);
        work.accept(start.plusSeconds(60));
        work.start(start.plusSeconds(120));
        return work;
    }

    private static Work assigned(Organization organization, long technicianId, Instant start) {
        Work work = Work.register(
                new OrganizationId(organization.id()),
                "늦은 작업",
                new MembershipId(organization.ownerMembership()),
                REGISTERED_AT,
                null,
                null,
                null);
        work.assign(
                new WorkSchedule(new TechnicianId(technicianId), start, Duration.ofHours(2)),
                start.minusSeconds(60),
                new MembershipId(organization.ownerMembership()));
        return work;
    }

    private Work stored(Organization organization, long workId) {
        return workRepository
                .findInOrganization(new OrganizationId(organization.id()), new WorkId(workId))
                .orElseThrow();
    }

    private long technicianIdOf(long organizationId, long accountId) {
        return jdbcTemplate.queryForObject(
                "select id from technician where company_id = ? and member_id = ?",
                Long.class,
                organizationId,
                accountId);
    }

    private long staffMembershipOf(long organizationId, long accountId) {
        return jdbcTemplate.queryForObject(
                "select id from company_memberships where company_id = ? and member_id = ?",
                Long.class,
                organizationId,
                accountId);
    }
}

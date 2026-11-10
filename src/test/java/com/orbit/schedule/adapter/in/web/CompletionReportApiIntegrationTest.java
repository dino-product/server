package com.orbit.schedule.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.ResultActions;

import com.orbit.schedule.CompletionReportSubmitted;
import com.orbit.schedule.WorkChanged;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.TechnicianId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkStatus;

/** 완료보고 제출·조회 API를 공통 응답·보안·트랜잭션·영속성·이벤트 발행과 함께 확인한다. 사진 업로드부터의 흐름은 WorkPhotoFlowIntegrationTest가 본다. */
@DisplayName("완료보고 제출·조회 API")
@RecordApplicationEvents
class CompletionReportApiIntegrationTest extends ScheduleApiTestSupport {

    private static final Instant REGISTERED_AT = Instant.parse("2020-01-01T00:00:00Z");
    private static final String REPORT = "/api/v1/organizations/{organizationId}/works/{workId}/completion-report";

    @Autowired
    private ApplicationEvents events;

    @Test
    @DisplayName("담당 기사가 작업중 작업에 보고를 내면 204이고 완료로 저장해 제출·저장 사실을 발행하며, 같은 보고를 다시 보내도 204이고 더 발행하지 않는다")
    void submitsReportAndIgnoresDuplicate() throws Exception {
        Fixture fixture = fixture();
        long workId = save(inProgress(fixture));
        Map<String, Object> report = report(1, "필터 1개", "배관 누수 수리", 150_000L, "CASH");

        submit(fixture.technician(), fixture.organization().id(), workId, report)
                .andExpect(status().isNoContent());

        Work saved = stored(fixture, workId);
        assertThat(saved.status()).isEqualTo(WorkStatus.COMPLETED);
        assertThat(saved.completedAt()).isPresent();
        assertThat(events.stream(CompletionReportSubmitted.class))
                .singleElement()
                .satisfies(event -> assertThat(event.workId()).isEqualTo(workId));
        assertThat(events.stream(WorkChanged.class)).hasSize(1);

        submit(fixture.technician(), fixture.organization().id(), workId, report)
                .andExpect(status().isNoContent());
        assertThat(events.stream(CompletionReportSubmitted.class)).hasSize(1);
        assertThat(events.stream(WorkChanged.class)).hasSize(1);
        submit(fixture.technician(), fixture.organization().id(), workId, report(1, "다른 부품", null, null, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULE-002"));
    }

    @Test
    @DisplayName("담당 기사·총관리자·직원은 제출한 보고를 조회하고, 완료 전이면 404 SCHEDULE-012, 다른 기사는 404 SCHEDULE-001이다")
    void readsSubmittedReport() throws Exception {
        Fixture fixture = fixture();
        long workId = save(inProgress(fixture));

        read(fixture.organization().owner(), fixture.organization().id(), workId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCHEDULE-012"));
        submit(fixture.technician(), fixture.organization().id(), workId, report(1, "필터 1개", null, 150_000L, "CASH"))
                .andExpect(status().isNoContent());

        long staff = account();
        staffMembership(fixture.organization().id(), staff);
        for (long requester :
                new long[] {fixture.technician(), fixture.organization().owner(), staff}) {
            read(requester, fixture.organization().id(), workId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.workId").value(workId))
                    .andExpect(jsonPath("$.result.technicianId").value(fixture.technicianId()))
                    .andExpect(jsonPath("$.result.report.usedParts").value("필터 1개"))
                    .andExpect(jsonPath("$.result.report.actualFee").value(150_000))
                    .andExpect(jsonPath("$.result.report.actualPaymentMethod").value("CASH"))
                    .andExpect(jsonPath("$.result.report.beforePhotos", empty()))
                    .andExpect(jsonPath("$.result.completedAt").exists());
        }
        long other = account();
        technicianContract(fixture.organization().id(), other);
        read(other, fixture.organization().id(), workId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCHEDULE-001"));
    }

    @Test
    @DisplayName("작업전 작업은 409 SCHEDULE-002, 관리자 제출은 403 SCHEDULE-005, 다른 기사는 404 SCHEDULE-001이고 저장하지 않는다")
    void rejectsNotInProgressManagerAndOtherTechnician() throws Exception {
        Fixture fixture = fixture();
        Work accepted = assigned(fixture);
        accepted.accept(Instant.now().minusSeconds(60));
        long acceptedId = save(accepted);
        long inProgressId = save(inProgress(fixture));
        long other = account();
        technicianContract(fixture.organization().id(), other);

        submit(fixture.technician(), fixture.organization().id(), acceptedId, report(1, null, null, null, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULE-002"));
        submit(
                        fixture.organization().owner(),
                        fixture.organization().id(),
                        inProgressId,
                        report(1, null, null, null, null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SCHEDULE-005"));
        submit(other, fixture.organization().id(), inProgressId, report(1, null, null, null, null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCHEDULE-001"));
        assertThat(stored(fixture, acceptedId).status()).isEqualTo(WorkStatus.ACCEPTED);
        assertThat(stored(fixture, inProgressId).status()).isEqualTo(WorkStatus.IN_PROGRESS);
        assertThat(events.stream(CompletionReportSubmitted.class)).isEmpty();
        assertThat(events.stream(WorkChanged.class)).isEmpty();
    }

    @Test
    @DisplayName("올리지 않은 사진은 400 SCHEDULE-022, 음수 금액은 400 SCHEDULE-003, 없는 결제 방식은 400 COMMON-400이다")
    void rejectsInvalidReport() throws Exception {
        Fixture fixture = fixture();
        long workId = save(inProgress(fixture));
        Map<String, Object> withUnknownPhoto = report(1, null, null, null, null);
        withUnknownPhoto.put("afterPhotoIds", List.of(UUID.randomUUID().toString()));

        submit(fixture.technician(), fixture.organization().id(), workId, withUnknownPhoto)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCHEDULE-022"));
        submit(fixture.technician(), fixture.organization().id(), workId, report(1, null, null, -1L, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCHEDULE-003"));
        submit(fixture.technician(), fixture.organization().id(), workId, report(1, null, null, null, "COUPON"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
        assertThat(stored(fixture, workId).status()).isEqualTo(WorkStatus.IN_PROGRESS);
    }

    /** 발주사와 그 발주사의 활성 기사 계약 하나. */
    private record Fixture(Organization organization, long technician, long technicianId) {}

    private Fixture fixture() throws Exception {
        Organization organization = organization();
        long technician = account();
        technicianContract(organization.id(), technician);
        return new Fixture(organization, technician, technicianIdOf(organization.id(), technician));
    }

    private static Map<String, Object> report(
            int assignmentNumber, String usedParts, String workNote, Long actualFee, String actualPaymentMethod) {
        Map<String, Object> body = new HashMap<>();
        body.put("assignmentNumber", assignmentNumber);
        body.put("usedParts", usedParts);
        body.put("workNote", workNote);
        body.put("actualFee", actualFee);
        body.put("actualPaymentMethod", actualPaymentMethod);
        return body;
    }

    private ResultActions submit(long requester, long organizationId, long workId, Map<String, Object> body)
            throws Exception {
        return mockMvc.perform(post(REPORT, organizationId, workId)
                .header(HttpHeaders.AUTHORIZATION, bearer(requester))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions read(long requester, long organizationId, long workId) throws Exception {
        return mockMvc.perform(
                get(REPORT, organizationId, workId).header(HttpHeaders.AUTHORIZATION, bearer(requester)));
    }

    /** 그 기사에게 30분 전부터 2시간 일정으로 배정한 작업. */
    private static Work assigned(Fixture fixture) {
        Work work = Work.register(
                new OrganizationId(fixture.organization().id()),
                "보일러 수리",
                new MembershipId(fixture.organization().ownerMembership()),
                REGISTERED_AT,
                null,
                null,
                null);
        work.assign(
                new WorkSchedule(
                        new TechnicianId(fixture.technicianId()),
                        Instant.now().minus(Duration.ofMinutes(30)),
                        Duration.ofHours(2)),
                Instant.now().minus(Duration.ofHours(1)),
                new MembershipId(fixture.organization().ownerMembership()));
        return work;
    }

    /** {@link #assigned}를 수락·시작한 작업. */
    private static Work inProgress(Fixture fixture) {
        Work work = assigned(fixture);
        work.accept(Instant.now().minus(Duration.ofMinutes(50)));
        work.start(Instant.now().minus(Duration.ofMinutes(25)));
        return work;
    }

    private Work stored(Fixture fixture, long workId) {
        return workRepository
                .findInOrganization(new OrganizationId(fixture.organization().id()), new WorkId(workId))
                .orElseThrow();
    }
}

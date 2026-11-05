package com.orbit.schedule.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.ResultActions;

import com.orbit.schedule.WorkAccepted;
import com.orbit.schedule.WorkChanged;
import com.orbit.schedule.WorkRejected;
import com.orbit.schedule.domain.AssignmentResult;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.TechnicianId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkStatus;

/** 기사 앱의 배정 응답·작업 시작 API를 공통 응답·보안·트랜잭션·영속성·이벤트 발행과 함께 확인한다. 준비물은 {@link ScheduleApiTestSupport}를 따른다. */
@DisplayName("기사 배정 응답·작업 시작 API")
@RecordApplicationEvents
class TechnicianWorkApiIntegrationTest extends ScheduleApiTestSupport {

    private static final Instant REGISTERED_AT = Instant.parse("2020-01-01T00:00:00Z");
    private static final String ACCEPTANCE = "/api/v1/organizations/{organizationId}/works/{workId}/acceptance";
    private static final String REJECTION = "/api/v1/organizations/{organizationId}/works/{workId}/rejection";
    private static final String START = "/api/v1/organizations/{organizationId}/works/{workId}/start";

    @Autowired
    private ApplicationEvents events;

    @Nested
    @DisplayName("수락")
    class Accept {

        @Test
        @DisplayName("담당 기사가 본 배정을 수락하면 204이고 작업전으로 저장해 수락·저장 사실을 발행하며, 같은 수락을 다시 보내도 204이고 더 발행하지 않는다")
        void acceptsAndIgnoresDuplicate() throws Exception {
            Fixture fixture = fixture();
            long workId = save(assigned(fixture.organization(), fixture.technicianId()));

            accept(fixture.technician(), fixture.organization().id(), workId, 1).andExpect(status().isNoContent());

            assertThat(stored(fixture.organization(), workId).status()).isEqualTo(WorkStatus.ACCEPTED);
            assertThat(events.stream(WorkAccepted.class)).singleElement().satisfies(event -> {
                assertThat(event.workId()).isEqualTo(workId);
                assertThat(event.technicianId()).isEqualTo(fixture.technicianId());
            });
            assertThat(events.stream(WorkChanged.class)).hasSize(1);

            accept(fixture.technician(), fixture.organization().id(), workId, 1).andExpect(status().isNoContent());
            assertThat(events.stream(WorkAccepted.class)).hasSize(1);
            assertThat(events.stream(WorkChanged.class)).hasSize(1);
        }

        @Test
        @DisplayName("일정 변경으로 배정이 바뀐 뒤 옛 순번이면 409 SCHEDULE-010, 없는 순번이면 403 SCHEDULE-009, 순번이 없으면 400 SCHEDULE-003이다")
        void rejectsStaleMissingAndAbsentAssignmentNumber() throws Exception {
            Fixture fixture = fixture();
            Work work = assigned(fixture.organization(), fixture.technicianId());
            work.reschedule(
                    work.schedule().orElseThrow().startTime().plus(Duration.ofHours(1)),
                    Duration.ofHours(2),
                    Instant.now().minusSeconds(30),
                    new MembershipId(fixture.organization().ownerMembership()));
            long workId = save(work);

            accept(fixture.technician(), fixture.organization().id(), workId, 1)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-010"));
            accept(fixture.technician(), fixture.organization().id(), workId, 3)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-009"));
            perform(ACCEPTANCE, fixture.technician(), fixture.organization().id(), workId, new HashMap<>())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-003"));
            assertThat(stored(fixture.organization(), workId).status()).isEqualTo(WorkStatus.PENDING_ACCEPT);
            assertThat(events.stream(WorkAccepted.class)).isEmpty();
        }

        @Test
        @DisplayName("관리자는 403 SCHEDULE-005, 구성원이 아니면 403 SCHEDULE-004, 다른 기사의 작업은 404 SCHEDULE-001이다")
        void rejectsManagerNonMemberAndOtherTechnician() throws Exception {
            Fixture fixture = fixture();
            long otherTechnician = account();
            technicianContract(fixture.organization().id(), otherTechnician);
            long workId = save(assigned(fixture.organization(), fixture.technicianId()));

            accept(fixture.organization().owner(), fixture.organization().id(), workId, 1)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-005"));
            accept(account(), fixture.organization().id(), workId, 1)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-004"));
            accept(otherTechnician, fixture.organization().id(), workId, 1)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-001"));
            assertThat(stored(fixture.organization(), workId).status()).isEqualTo(WorkStatus.PENDING_ACCEPT);
        }

        @Test
        @DisplayName("본문이 없거나 숫자가 아닌 식별자는 400 COMMON-400, 토큰이 없으면 401이다")
        void rejectsMalformedRequestAndAnonymous() throws Exception {
            Fixture fixture = fixture();
            long workId = save(assigned(fixture.organization(), fixture.technicianId()));

            mockMvc.perform(post(ACCEPTANCE, fixture.organization().id(), workId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(fixture.technician())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMMON-400"));
            mockMvc.perform(post(ACCEPTANCE, fixture.organization().id(), "abc")
                            .header(HttpHeaders.AUTHORIZATION, bearer(fixture.technician()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"assignmentNumber\":1}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMMON-400"));
            mockMvc.perform(post(ACCEPTANCE, fixture.organization().id(), workId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"assignmentNumber\":1}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("COMMON-401"));
        }
    }

    @Nested
    @DisplayName("거절")
    class Reject {

        @Test
        @DisplayName("담당 기사가 사유와 함께 거절하면 204이고 대기함으로 저장해 거절 사유 코드·메모를 발행하며, 다시 거절하면 볼 수 없는 작업이라 404 SCHEDULE-001이다")
        void rejectsAndHidesWork() throws Exception {
            Fixture fixture = fixture();
            long workId = save(assigned(fixture.organization(), fixture.technicianId()));

            reject(fixture.technician(), fixture.organization().id(), workId, 1, "OTHER", "차량 고장")
                    .andExpect(status().isNoContent());

            Work saved = stored(fixture.organization(), workId);
            assertThat(saved.status()).isEqualTo(WorkStatus.REGISTERED);
            assertThat(saved.assignmentHistory().getLast().result()).isEqualTo(AssignmentResult.REJECTED);
            assertThat(events.stream(WorkRejected.class)).singleElement().satisfies(event -> {
                assertThat(event.workId()).isEqualTo(workId);
                assertThat(event.technicianId()).isEqualTo(fixture.technicianId());
                assertThat(event.reasonCode()).isEqualTo("OTHER");
                assertThat(event.reasonNote()).isEqualTo("차량 고장");
            });
            assertThat(events.stream(WorkChanged.class)).hasSize(1);

            reject(fixture.technician(), fixture.organization().id(), workId, 1, "OTHER", "차량 고장")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-001"));
            assertThat(events.stream(WorkRejected.class)).hasSize(1);
        }

        @Test
        @DisplayName("기타인데 메모가 없거나 기타가 아닌데 메모가 있으면 400 SCHEDULE-003, 목록에 없는 사유는 400 COMMON-400이다")
        void rejectsInvalidReason() throws Exception {
            Fixture fixture = fixture();
            long workId = save(assigned(fixture.organization(), fixture.technicianId()));

            reject(fixture.technician(), fixture.organization().id(), workId, 1, "OTHER", null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-003"));
            reject(fixture.technician(), fixture.organization().id(), workId, 1, "LOCATION_TOO_FAR", "멀어요")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-003"));
            reject(fixture.technician(), fixture.organization().id(), workId, 1, "TOO_BUSY", null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMMON-400"));
            assertThat(stored(fixture.organization(), workId).status()).isEqualTo(WorkStatus.PENDING_ACCEPT);
            assertThat(events.stream(WorkRejected.class)).isEmpty();
        }

        @Test
        @DisplayName("이미 수락한 작업을 거절하면 409 SCHEDULE-002이고 작업전을 유지한다")
        void rejectsAcceptedWork() throws Exception {
            Fixture fixture = fixture();
            Work work = assigned(fixture.organization(), fixture.technicianId());
            work.accept(Instant.now().minusSeconds(30));
            long workId = save(work);

            reject(fixture.technician(), fixture.organization().id(), workId, 1, "SCHEDULE_CONFLICT", null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-002"));
            assertThat(stored(fixture.organization(), workId).status()).isEqualTo(WorkStatus.ACCEPTED);
            assertThat(events.stream(WorkRejected.class)).isEmpty();
        }

        @Test
        @DisplayName("관리자는 403 SCHEDULE-005, 다른 기사의 작업은 404 SCHEDULE-001이다")
        void rejectsManagerAndOtherTechnician() throws Exception {
            Fixture fixture = fixture();
            long otherTechnician = account();
            technicianContract(fixture.organization().id(), otherTechnician);
            long workId = save(assigned(fixture.organization(), fixture.technicianId()));

            reject(fixture.organization().owner(), fixture.organization().id(), workId, 1, "SCHEDULE_CONFLICT", null)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-005"));
            reject(otherTechnician, fixture.organization().id(), workId, 1, "SCHEDULE_CONFLICT", null)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-001"));
            assertThat(stored(fixture.organization(), workId).status()).isEqualTo(WorkStatus.PENDING_ACCEPT);
        }
    }

    @Nested
    @DisplayName("작업 시작")
    class Start {

        @Test
        @DisplayName("예정 시작 10분 안의 작업전 작업을 시작하면 204이고 작업중·시작 시각을 저장해 저장 사실을 발행하며, 다시 보내도 204이고 더 발행하지 않는다")
        void startsAndIgnoresDuplicate() throws Exception {
            Fixture fixture = fixture();
            long workId = save(accepted(
                    fixture.organization(),
                    fixture.technicianId(),
                    Instant.now().plus(Duration.ofMinutes(5))));

            start(fixture.technician(), fixture.organization().id(), workId, 1).andExpect(status().isNoContent());

            Work saved = stored(fixture.organization(), workId);
            assertThat(saved.status()).isEqualTo(WorkStatus.IN_PROGRESS);
            assertThat(saved.startedAt()).isPresent();
            assertThat(events.stream(WorkChanged.class)).hasSize(1);

            start(fixture.technician(), fixture.organization().id(), workId, 1).andExpect(status().isNoContent());
            assertThat(stored(fixture.organization(), workId).startedAt()).isEqualTo(saved.startedAt());
            assertThat(events.stream(WorkChanged.class)).hasSize(1);
        }

        @Test
        @DisplayName("같은 기사의 다른 작업이 작업중이어도 시작할 수 있다")
        void startsWhileAnotherWorkIsInProgress() throws Exception {
            Fixture fixture = fixture();
            long first = save(accepted(
                    fixture.organization(),
                    fixture.technicianId(),
                    Instant.now().plus(Duration.ofMinutes(5))));
            long second = save(accepted(
                    fixture.organization(),
                    fixture.technicianId(),
                    Instant.now().plus(Duration.ofMinutes(8))));

            start(fixture.technician(), fixture.organization().id(), first, 1).andExpect(status().isNoContent());
            start(fixture.technician(), fixture.organization().id(), second, 1).andExpect(status().isNoContent());

            assertThat(stored(fixture.organization(), second).status()).isEqualTo(WorkStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("예정 시작 10분 전보다 이르면 409 SCHEDULE-017, 예정 종료가 지났으면 409 SCHEDULE-018이고 작업전을 유지한다")
        void rejectsOutsideStartWindow() throws Exception {
            Fixture fixture = fixture();
            long early = save(accepted(
                    fixture.organization(),
                    fixture.technicianId(),
                    Instant.now().plus(Duration.ofHours(1))));
            long late = save(accepted(
                    fixture.organization(),
                    fixture.technicianId(),
                    Instant.now().minus(Duration.ofHours(3))));

            start(fixture.technician(), fixture.organization().id(), early, 1)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-017"));
            start(fixture.technician(), fixture.organization().id(), late, 1)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-018"));
            assertThat(stored(fixture.organization(), early).status()).isEqualTo(WorkStatus.ACCEPTED);
            assertThat(stored(fixture.organization(), late).status()).isEqualTo(WorkStatus.ACCEPTED);
            assertThat(events.stream(WorkChanged.class)).isEmpty();
        }

        @Test
        @DisplayName("수락 전 작업은 409 SCHEDULE-002, 관리자는 403 SCHEDULE-005이다")
        void rejectsPendingWorkAndManager() throws Exception {
            Fixture fixture = fixture();
            long pending = save(assigned(
                    fixture.organization(),
                    fixture.technicianId(),
                    Instant.now().plus(Duration.ofMinutes(5))));
            long acceptedId = save(accepted(
                    fixture.organization(),
                    fixture.technicianId(),
                    Instant.now().plus(Duration.ofMinutes(5))));

            start(fixture.technician(), fixture.organization().id(), pending, 1)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-002"));
            start(fixture.organization().owner(), fixture.organization().id(), acceptedId, 1)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-005"));
            assertThat(stored(fixture.organization(), pending).status()).isEqualTo(WorkStatus.PENDING_ACCEPT);
            assertThat(stored(fixture.organization(), acceptedId).status()).isEqualTo(WorkStatus.ACCEPTED);
        }
    }

    /** 발주사와 그 발주사의 활성 기사 계약 하나. */
    private record Fixture(Organization organization, long technician, long technicianId) {}

    private Fixture fixture() throws Exception {
        Organization organization = organization();
        long technician = account();
        technicianContract(organization.id(), technician);
        return new Fixture(organization, technician, technicianIdOf(organization.id(), technician));
    }

    private ResultActions accept(long requester, long organizationId, long workId, int assignmentNumber)
            throws Exception {
        return perform(ACCEPTANCE, requester, organizationId, workId, Map.of("assignmentNumber", assignmentNumber));
    }

    private ResultActions start(long requester, long organizationId, long workId, int assignmentNumber)
            throws Exception {
        return perform(START, requester, organizationId, workId, Map.of("assignmentNumber", assignmentNumber));
    }

    private ResultActions reject(
            long requester, long organizationId, long workId, int assignmentNumber, String reason, String note)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("assignmentNumber", assignmentNumber);
        body.put("reason", reason);
        body.put("note", note);
        return perform(REJECTION, requester, organizationId, workId, body);
    }

    private ResultActions perform(String path, long requester, long organizationId, long workId, Map<String, ?> body)
            throws Exception {
        return mockMvc.perform(post(path, organizationId, workId)
                .header(HttpHeaders.AUTHORIZATION, bearer(requester))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    /** 주어진 기사에게 하루 뒤부터 2시간 일정으로 배정해 수락을 기다리는 작업. */
    private static Work assigned(Organization organization, long technicianId) {
        return assigned(organization, technicianId, Instant.now().plus(Duration.ofDays(1)));
    }

    /** 주어진 기사에게 start부터 2시간 일정으로 배정하고 수락한 작업. */
    private static Work accepted(Organization organization, long technicianId, Instant start) {
        Work work = assigned(organization, technicianId, start);
        work.accept(Instant.now().minusSeconds(30));
        return work;
    }

    private static Work assigned(Organization organization, long technicianId, Instant start) {
        Work work = Work.register(
                new OrganizationId(organization.id()),
                "보일러 점검",
                new MembershipId(organization.ownerMembership()),
                REGISTERED_AT,
                null,
                null,
                null);
        work.assign(
                new WorkSchedule(new TechnicianId(technicianId), start, Duration.ofHours(2)),
                Instant.now().minusSeconds(60),
                new MembershipId(organization.ownerMembership()));
        return work;
    }

    private Work stored(Organization organization, long workId) {
        return workRepository
                .findInOrganization(new OrganizationId(organization.id()), new WorkId(workId))
                .orElseThrow();
    }
}

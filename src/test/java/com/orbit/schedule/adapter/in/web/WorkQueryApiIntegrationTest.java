package com.orbit.schedule.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import com.orbit.schedule.application.port.out.StaffProfile;
import com.orbit.schedule.application.port.out.TechnicianProfile;
import com.orbit.schedule.application.port.out.TypeLabel;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.PlannedSchedule;
import com.orbit.schedule.domain.Rejection;
import com.orbit.schedule.domain.RejectionReason;
import com.orbit.schedule.domain.TechnicianId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkTypeId;

/**
 * 작업 조회 API를 공통 응답·보안·영속성과 함께 확인한다. 준비물과 표시 정보 포트 대체는 {@link ScheduleApiTestSupport}를 따른다.
 */
@DisplayName("작업 조회 API")
class WorkQueryApiIntegrationTest extends ScheduleApiTestSupport {

    private static final Instant DAY = Instant.parse("2030-03-02T00:00:00Z");
    private static final TechnicianId TECHNICIAN = new TechnicianId(41L);
    private static final TypeLabel GENERAL = new TypeLabel(7L, "일반 기사", "#3366FF", false);
    private static final TypeLabel INSTALL = new TypeLabel(3L, "설치", "#7C5CFF", false);

    @Nested
    @DisplayName("타임테이블")
    class Timetable {

        @Test
        @DisplayName("기사 행, 구간의 작업 칸, 대기함 카드를 표시 정보와 함께 돌려준다")
        void returnsTimetable() throws Exception {
            Organization organization = organization();
            givenDisplayInfos(organization);
            Work assigned = registered(organization, "인덕션 설치", Instant.now().minus(Duration.ofHours(1)));
            assigned.assign(
                    new WorkSchedule(TECHNICIAN, DAY.plus(Duration.ofHours(1)), Duration.ofHours(2)),
                    Instant.now().minusSeconds(60),
                    new MembershipId(organization.ownerMembership()));
            long assignedId = save(assigned);
            Work rejected = registered(organization, "거절된 작업", Instant.now().minus(Duration.ofHours(2)));
            rejected.assign(
                    new WorkSchedule(TECHNICIAN, DAY.plus(Duration.ofHours(5)), Duration.ofHours(1)),
                    Instant.now().minusSeconds(120),
                    new MembershipId(organization.ownerMembership()));
            rejected.reject(
                    new Rejection(RejectionReason.OTHER, "너무 멀어요"),
                    Instant.now().minusSeconds(90));
            long rejectedId = save(rejected);
            long plannedId = save(Work.register(
                    new OrganizationId(organization.id()),
                    "기사만 정한 작업",
                    new MembershipId(organization.ownerMembership()),
                    Instant.now().minus(Duration.ofHours(3)),
                    null,
                    null,
                    null,
                    new PlannedSchedule(null, DAY, Duration.ofMinutes(90))));

            timetable(organization.owner(), organization.id(), "from=" + DAY + "&to=" + DAY.plus(Duration.ofDays(1)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.technicians[0].technicianId").value(TECHNICIAN.value()))
                    .andExpect(jsonPath("$.result.technicians[0].name").value("김민수"))
                    .andExpect(jsonPath("$.result.technicians[0].technicianType.name")
                            .value("일반 기사"))
                    .andExpect(jsonPath("$.result.scheduledWorks.length()").value(1))
                    .andExpect(jsonPath("$.result.scheduledWorks[0].workId").value(assignedId))
                    .andExpect(jsonPath("$.result.scheduledWorks[0].status").value("PENDING_ACCEPTANCE"))
                    .andExpect(jsonPath("$.result.scheduledWorks[0].technician.name")
                            .value("김민수"))
                    .andExpect(
                            jsonPath("$.result.scheduledWorks[0].workType.name").value("설치"))
                    .andExpect(jsonPath("$.result.scheduledWorks[0].registrar.name")
                            .value("박서준"))
                    .andExpect(jsonPath("$.result.scheduledWorks[0].registrar.owner")
                            .value(true))
                    .andExpect(jsonPath("$.result.scheduledWorks[0].endTime")
                            .value(DAY.plus(Duration.ofHours(3)).toString()))
                    .andExpect(jsonPath("$.result.backlog.length()").value(2))
                    .andExpect(jsonPath("$.result.backlog[0].workId").value(plannedId))
                    .andExpect(jsonPath("$.result.backlog[0].plannedExpectedDurationMinutes")
                            .value(90))
                    .andExpect(jsonPath("$.result.backlog[0].technicianMissing").value(true))
                    .andExpect(jsonPath("$.result.backlog[0].timeMissing").value(false))
                    .andExpect(jsonPath("$.result.backlog[1].workId").value(rejectedId))
                    .andExpect(jsonPath("$.result.backlog[1].rejectionReason").value("OTHER"))
                    .andExpect(jsonPath("$.result.backlog[1].rejectionNote").value("너무 멀어요"))
                    .andExpect(jsonPath("$.result.backlog[1].stale").value(false));
        }

        @Test
        @DisplayName("유형 조건은 반복하거나 쉼표로 이은 식별자 목록으로 받고, 비우면 거르지 않는다")
        void acceptsTypeIdLists() throws Exception {
            Organization organization = organization();
            givenDisplayInfos(organization);
            Work assigned = registered(organization, "인덕션 설치", Instant.now().minus(Duration.ofHours(1)));
            assigned.assign(
                    new WorkSchedule(TECHNICIAN, DAY.plus(Duration.ofHours(1)), Duration.ofHours(2)),
                    Instant.now().minusSeconds(60),
                    new MembershipId(organization.ownerMembership()));
            save(assigned);
            String period = "from=" + DAY + "&to=" + DAY.plus(Duration.ofDays(1));

            timetable(organization.owner(), organization.id(), period + "&technicianTypeIds=7,8&workTypeIds=3")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.scheduledWorks.length()").value(1));
            timetable(organization.owner(), organization.id(), period + "&workTypeIds=4&workTypeIds=3")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.scheduledWorks.length()").value(1));
            timetable(organization.owner(), organization.id(), period + "&workTypeIds=4")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.scheduledWorks.length()").value(0))
                    .andExpect(jsonPath("$.result.technicians.length()").value(1));
            timetable(organization.owner(), organization.id(), period + "&technicianTypeIds=")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.scheduledWorks.length()").value(1));
        }

        @Test
        @DisplayName("구간이 없거나 31일을 넘거나 유형 식별자가 양수가 아니면 SCHEDULE-003, 시각·숫자 형식이 틀리면 COMMON-400이다")
        void rejectsInvalidPeriod() throws Exception {
            Organization organization = organization();

            timetable(organization.owner(), organization.id(), "to=" + DAY)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-003"));
            timetable(organization.owner(), organization.id(), "from=" + DAY + "&to=" + DAY.plus(Duration.ofDays(32)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-003"));
            timetable(organization.owner(), organization.id(), "from=2030-03-02&to=" + DAY)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMMON-400"));
            String period = "from=" + DAY + "&to=" + DAY.plus(Duration.ofDays(1));
            timetable(organization.owner(), organization.id(), period + "&technicianTypeIds=abc")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMMON-400"));
            timetable(organization.owner(), organization.id(), period + "&technicianTypeIds=0")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-003"));
        }

        @Test
        @DisplayName("구성원이 아니면 SCHEDULE-004, 기사면 SCHEDULE-005, 인증이 없으면 401이다")
        void rejectsNonManagers() throws Exception {
            Organization organization = organization();
            long technician = account();
            technicianContract(organization.id(), technician);
            String period = "from=" + DAY + "&to=" + DAY.plus(Duration.ofDays(1));

            timetable(account(), organization.id(), period)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-004"));
            timetable(technician, organization.id(), period)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-005"));
            mockMvc.perform(get("/api/v1/organizations/{organizationId}/timetable?" + period, organization.id()))
                    .andExpect(status().isUnauthorized());
        }

        private ResultActions timetable(long requester, long organizationId, String query) throws Exception {
            return mockMvc.perform(get("/api/v1/organizations/{organizationId}/timetable?" + query, organizationId)
                    .header(HttpHeaders.AUTHORIZATION, bearer(requester)));
        }
    }

    private void givenDisplayInfos(Organization organization) {
        OrganizationId organizationId = new OrganizationId(organization.id());
        TechnicianProfile kim = new TechnicianProfile(TECHNICIAN, "김민수", GENERAL);
        given(technicianProfiles.listActive(organizationId)).willReturn(List.of(kim));
        given(technicianProfiles.findProfiles(eq(organizationId), any())).willReturn(Map.of(TECHNICIAN, kim));
        MembershipId owner = new MembershipId(organization.ownerMembership());
        given(staffProfiles.findProfiles(eq(organizationId), any()))
                .willReturn(Map.of(owner, new StaffProfile(owner, "박서준", true, null)));
        given(workTypeLabels.findLabels(eq(organizationId), any()))
                .willReturn(Map.of(new WorkTypeId(INSTALL.id()), INSTALL));
    }

    private static Work registered(Organization organization, String name, Instant registeredAt) {
        return Work.register(
                new OrganizationId(organization.id()),
                name,
                new MembershipId(organization.ownerMembership()),
                registeredAt,
                new WorkTypeId(INSTALL.id()),
                null,
                null);
    }
}

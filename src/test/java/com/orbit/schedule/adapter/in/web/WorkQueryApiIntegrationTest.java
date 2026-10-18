package com.orbit.schedule.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import com.orbit.schedule.application.port.out.StaffProfile;
import com.orbit.schedule.application.port.out.TechnicianProfile;
import com.orbit.schedule.application.port.out.TypeLabel;
import com.orbit.schedule.domain.CompletionReport;
import com.orbit.schedule.domain.CustomerInfo;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.PlannedSchedule;
import com.orbit.schedule.domain.Rejection;
import com.orbit.schedule.domain.RejectionReason;
import com.orbit.schedule.domain.SiteAddress;
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
            // 4일 전에 등록해 방금 거절로 돌아온 작업: 방치는 등록 시각부터 센다.
            Work rejected = registered(organization, "거절된 작업", Instant.now().minus(Duration.ofDays(4)));
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
                    .andExpect(jsonPath("$.result.scheduledWorks[0].version").value(0))
                    .andExpect(jsonPath("$.result.backlog.length()").value(2))
                    .andExpect(jsonPath("$.result.backlog[0].workId").value(rejectedId))
                    .andExpect(jsonPath("$.result.backlog[0].rejectionReason").value("OTHER"))
                    .andExpect(jsonPath("$.result.backlog[0].rejectionNote").value("너무 멀어요"))
                    .andExpect(jsonPath("$.result.backlog[0].stale").value(true))
                    .andExpect(jsonPath("$.result.backlog[1].workId").value(plannedId))
                    .andExpect(jsonPath("$.result.backlog[1].plannedExpectedDurationMinutes")
                            .value(90))
                    .andExpect(jsonPath("$.result.backlog[1].technicianMissing").value(true))
                    .andExpect(jsonPath("$.result.backlog[1].timeMissing").value(false))
                    .andExpect(jsonPath("$.result.backlog[1].stale").value(false))
                    .andExpect(jsonPath("$.result.backlog[1].version").value(0));
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

    @Nested
    @DisplayName("작업 목록")
    class WorkList {

        @Test
        @DisplayName("조건에 맞는 작업을 표시 정보·등록·수정 시각과 함께 정렬해 쪽으로 돌려준다")
        void searchesWorks() throws Exception {
            Organization organization = organization();
            givenDisplayInfos(organization);
            long older = save(registered(organization, "먼저 등록", Instant.now().minus(Duration.ofHours(2))));
            Work assigned = registered(organization, "배정된 작업", Instant.now().minus(Duration.ofHours(1)));
            assigned.assign(
                    new WorkSchedule(TECHNICIAN, DAY, Duration.ofHours(2)),
                    Instant.now().minusSeconds(60),
                    new MembershipId(organization.ownerMembership()));
            long newer = save(assigned);
            save(Work.register(
                    new OrganizationId(organization.id()),
                    "유형 없는 작업",
                    new MembershipId(organization.ownerMembership()),
                    Instant.now().minus(Duration.ofHours(3)),
                    null,
                    null,
                    null));

            works(organization.owner(), organization.id(), "workTypeId=" + INSTALL.id() + "&sort=REGISTERED_ASC&size=1")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.totalCount").value(2))
                    .andExpect(jsonPath("$.result.size").value(1))
                    .andExpect(jsonPath("$.result.works.length()").value(1))
                    .andExpect(jsonPath("$.result.works[0].workId").value(older))
                    .andExpect(jsonPath("$.result.works[0].workType.name").value("설치"))
                    .andExpect(jsonPath("$.result.works[0].registrar.name").value("박서준"))
                    .andExpect(jsonPath("$.result.works[0].registeredAt").exists())
                    .andExpect(jsonPath("$.result.works[0].lastModifiedAt").exists());
            works(organization.owner(), organization.id(), "statuses=PENDING_ACCEPTANCE&statuses=ACCEPTED")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.totalCount").value(1));
            works(
                            organization.owner(),
                            organization.id(),
                            "statuses=PENDING_ACCEPTANCE,ACCEPTED&registrarId=" + organization.ownerMembership())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.totalCount").value(1))
                    .andExpect(jsonPath("$.result.works[0].workId").value(newer))
                    .andExpect(jsonPath("$.result.works[0].technician.name").value("김민수"))
                    .andExpect(jsonPath("$.result.works[0].startTime").value(DAY.toString()));
        }

        @Test
        @DisplayName("거절로 돌아온 대기함 작업은 마지막 배정의 기사·시간을 보이고 그 기사·기간 조건에도 걸린다")
        void showsLastAssignmentOfReturnedWork() throws Exception {
            Organization organization = organization();
            givenDisplayInfos(organization);
            Work rejected = registered(organization, "거절된 작업", Instant.now().minus(Duration.ofHours(1)));
            rejected.assign(
                    new WorkSchedule(TECHNICIAN, DAY, Duration.ofHours(2)),
                    Instant.now().minusSeconds(120),
                    new MembershipId(organization.ownerMembership()));
            rejected.reject(
                    new Rejection(RejectionReason.SCHEDULE_CONFLICT, null),
                    Instant.now().minusSeconds(60));
            long rejectedId = save(rejected);

            works(
                            organization.owner(),
                            organization.id(),
                            "technicianId=" + TECHNICIAN.value() + "&startFrom=" + DAY + "&startTo="
                                    + DAY.plus(Duration.ofDays(1)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.totalCount").value(1))
                    .andExpect(jsonPath("$.result.works[0].workId").value(rejectedId))
                    .andExpect(jsonPath("$.result.works[0].status").value("REGISTERED"))
                    .andExpect(jsonPath("$.result.works[0].returnedByRejection").value(true))
                    .andExpect(jsonPath("$.result.works[0].technician.name").value("김민수"))
                    .andExpect(jsonPath("$.result.works[0].startTime").value(DAY.toString()))
                    .andExpect(jsonPath("$.result.works[0].endTime")
                            .value(DAY.plus(Duration.ofHours(2)).toString()));
        }

        @Test
        @DisplayName("목록에 없는 상태·정렬이나 숫자 형식 오류는 COMMON-400, 범위를 벗어난 조건은 SCHEDULE-003이다")
        void rejectsInvalidCriteria() throws Exception {
            Organization organization = organization();

            works(organization.owner(), organization.id(), "statuses=WAITING")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMMON-400"));
            works(organization.owner(), organization.id(), "sort=NAME")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMMON-400"));
            works(organization.owner(), organization.id(), "workTypeId=abc")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMMON-400"));
            works(organization.owner(), organization.id(), "registrarId=0")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-003"));
            works(organization.owner(), organization.id(), "size=101")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-003"));
            mockMvc.perform(get("/api/v1/organizations/{organizationId}/works/backlog?size=abc", organization.id())
                            .header(HttpHeaders.AUTHORIZATION, bearer(organization.owner())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMMON-400"));
        }

        @Test
        @DisplayName("대기함 요약은 전체·등록·거절 건수와 오래된 순의 대기함 카드를 쪽으로 돌려준다")
        void returnsBacklogSummary() throws Exception {
            Organization organization = organization();
            givenDisplayInfos(organization);
            long oldest = save(registered(organization, "오래된 대기", Instant.now().minus(Duration.ofHours(3))));
            Work rejected = registered(organization, "거절된 작업", Instant.now().minus(Duration.ofHours(2)));
            rejected.assign(
                    new WorkSchedule(TECHNICIAN, DAY, Duration.ofHours(1)),
                    Instant.now().minusSeconds(120),
                    new MembershipId(organization.ownerMembership()));
            rejected.reject(
                    new Rejection(RejectionReason.SCHEDULE_CONFLICT, null),
                    Instant.now().minusSeconds(90));
            long rejectedId = save(rejected);
            Work assigned = registered(organization, "배정된 작업", Instant.now().minus(Duration.ofHours(1)));
            assigned.assign(
                    new WorkSchedule(TECHNICIAN, DAY.plus(Duration.ofHours(3)), Duration.ofHours(1)),
                    Instant.now().minusSeconds(60),
                    new MembershipId(organization.ownerMembership()));
            save(assigned);

            mockMvc.perform(get("/api/v1/organizations/{organizationId}/works/backlog?size=1", organization.id())
                            .header(HttpHeaders.AUTHORIZATION, bearer(organization.owner())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.totalCount").value(2))
                    .andExpect(jsonPath("$.result.registeredCount").value(1))
                    .andExpect(jsonPath("$.result.rejectedCount").value(1))
                    .andExpect(jsonPath("$.result.works.length()").value(1))
                    .andExpect(jsonPath("$.result.works[0].workId").value(oldest))
                    .andExpect(jsonPath("$.result.works[0].technicianMissing").value(true));
            mockMvc.perform(get("/api/v1/organizations/{organizationId}/works/backlog?page=1&size=1", organization.id())
                            .header(HttpHeaders.AUTHORIZATION, bearer(organization.owner())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.works[0].workId").value(rejectedId))
                    .andExpect(jsonPath("$.result.works[0].rejectionReason").value("SCHEDULE_CONFLICT"));
        }

        @Test
        @DisplayName("기사는 작업 목록·대기함 요약·작업내역 출력을 쓸 수 없다(SCHEDULE-005)")
        void rejectsTechnician() throws Exception {
            Organization organization = organization();
            long technician = account();
            technicianContract(organization.id(), technician);

            works(technician, organization.id(), "")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-005"));
            mockMvc.perform(get("/api/v1/organizations/{organizationId}/works/backlog", organization.id())
                            .header(HttpHeaders.AUTHORIZATION, bearer(technician)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-005"));
            mockMvc.perform(get("/api/v1/organizations/{organizationId}/works/export", organization.id())
                            .header(HttpHeaders.AUTHORIZATION, bearer(technician)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-005"));
        }

        @Test
        @DisplayName("작업내역 출력은 검색 조건의 결과를 xlsx 파일로 내려준다")
        void exportsWorksAsXlsx() throws Exception {
            Organization organization = organization();
            givenDisplayInfos(organization);
            Work assigned = registered(organization, "배정된 작업", Instant.now().minus(Duration.ofHours(1)));
            assigned.assign(
                    new WorkSchedule(TECHNICIAN, DAY, Duration.ofHours(2)),
                    Instant.now().minusSeconds(60),
                    new MembershipId(organization.ownerMembership()));
            save(assigned);
            save(registered(organization, "다른 상태 작업", Instant.now().minus(Duration.ofHours(2))));

            byte[] file = mockMvc.perform(get(
                                    "/api/v1/organizations/{organizationId}/works/export?statuses=PENDING_ACCEPTANCE"
                                            + "&timeZone=Asia/Seoul",
                                    organization.id())
                            .header(HttpHeaders.AUTHORIZATION, bearer(organization.owner())))
                    .andExpect(status().isOk())
                    .andExpect(header().string(
                                    HttpHeaders.CONTENT_TYPE,
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("filename*=UTF-8''")))
                    .andReturn()
                    .getResponse()
                    .getContentAsByteArray();

            try (ReadableWorkbook workbook = new ReadableWorkbook(new ByteArrayInputStream(file))) {
                List<Row> rows = workbook.getFirstSheet().read();
                assertThat(rows).hasSize(2);
                assertThat(rows.get(1).getCellText(0)).isEqualTo("배정된 작업");
                assertThat(rows.get(1).getCellText(2)).isEqualTo("김민수");
                assertThat(rows.get(1).getCellText(4)).isEqualTo("2030-03-02 09:00 ~ 11:00");
            }
        }

        @Test
        @DisplayName("작업내역 출력은 알 수 없는 시간대면 COMMON-400이다")
        void rejectsUnknownTimeZone() throws Exception {
            Organization organization = organization();

            mockMvc.perform(get(
                                    "/api/v1/organizations/{organizationId}/works/export?timeZone=Mars/Base",
                                    organization.id())
                            .header(HttpHeaders.AUTHORIZATION, bearer(organization.owner())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMMON-400"));
        }

        @Test
        @DisplayName("다른 출처의 화면도 내려받은 파일 이름을 읽도록 Content-Disposition을 노출한다")
        void exposesFileNameHeaderToBrowsers() throws Exception {
            Organization organization = organization();

            mockMvc.perform(get("/api/v1/organizations/{organizationId}/works/export", organization.id())
                            .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                            .header(HttpHeaders.AUTHORIZATION, bearer(organization.owner())))
                    .andExpect(status().isOk())
                    .andExpect(header().string(
                                    HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, containsString("Content-Disposition")));
        }

        private ResultActions works(long requester, long organizationId, String query) throws Exception {
            return mockMvc.perform(get("/api/v1/organizations/{organizationId}/works?" + query, organizationId)
                    .header(HttpHeaders.AUTHORIZATION, bearer(requester)));
        }
    }

    @Nested
    @DisplayName("작업 상세")
    class Detail {

        @Test
        @DisplayName("관리자는 작업유형·등록자·등록 시각·일정(기사 이름·유형)·배정 이력을 받는다")
        void returnsDetailForManager() throws Exception {
            Organization organization = organization();
            givenDisplayInfos(organization);
            Instant registeredAt = Instant.now().minus(Duration.ofHours(1)).truncatedTo(ChronoUnit.SECONDS);
            Work work = registered(organization, "배관 누수 점검", registeredAt);
            work.assign(
                    new WorkSchedule(TECHNICIAN, DAY, Duration.ofHours(2)),
                    Instant.now().minusSeconds(60),
                    new MembershipId(organization.ownerMembership()));
            long workId = save(work);

            detail(organization.owner(), organization.id(), workId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.workId").value(workId))
                    .andExpect(jsonPath("$.result.workType.name").value("설치"))
                    .andExpect(jsonPath("$.result.registrar.name").value("박서준"))
                    .andExpect(jsonPath("$.result.registeredAt").value(registeredAt.toString()))
                    .andExpect(jsonPath("$.result.schedule.technician.name").value("김민수"))
                    .andExpect(jsonPath("$.result.schedule.technician.technicianType.name")
                            .value("일반 기사"))
                    .andExpect(jsonPath("$.result.schedule.expectedDurationMinutes")
                            .value(120))
                    .andExpect(
                            jsonPath("$.result.assignments[0].assignmentNumber").value(1))
                    .andExpect(jsonPath("$.result.assignments[0].result").value("PENDING"))
                    .andExpect(jsonPath("$.result.statusCorrections").doesNotExist())
                    .andExpect(jsonPath("$.result.version").value(0));
        }

        @Test
        @DisplayName("고객 정보의 현장 주소를 우편번호·기본주소·상세주소로 나눠 돌려준다")
        void returnsSiteAddressParts() throws Exception {
            Organization organization = organization();
            long workId = save(Work.register(
                    new OrganizationId(organization.id()),
                    "주소 작업",
                    new MembershipId(organization.ownerMembership()),
                    Instant.now().minus(Duration.ofHours(1)),
                    null,
                    new CustomerInfo("이정호", null, new SiteAddress("04043", "서울시 마포구 양화로 45", "3층 302호")),
                    null));

            detail(organization.owner(), organization.id(), workId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.customer.name").value("이정호"))
                    .andExpect(jsonPath("$.result.customer.phone").value(nullValue()))
                    .andExpect(jsonPath("$.result.customer.postalCode").value("04043"))
                    .andExpect(jsonPath("$.result.customer.address").value("서울시 마포구 양화로 45"))
                    .andExpect(jsonPath("$.result.customer.addressDetail").value("3층 302호"));
        }

        @Test
        @DisplayName("등록 때 붙인 현장 사진을 첨부 파일(식별자·주소)로 돌려준다")
        void returnsSitePhotosAsAttachments() throws Exception {
            Organization organization = organization();
            Work work = registered(organization, "현장 사진 작업", Instant.now().minus(Duration.ofHours(1)));
            work.replaceSitePhotos(List.of("site-1", "site-2"));
            long workId = save(work);

            detail(organization.owner(), organization.id(), workId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.sitePhotos.length()").value(2))
                    .andExpect(jsonPath("$.result.sitePhotos[0].photoId").value("site-1"))
                    .andExpect(jsonPath("$.result.sitePhotos[1].url").exists());
        }

        @Test
        @DisplayName("다른 조직의 작업은 SCHEDULE-001로 숨긴다")
        void hidesWorkOfOtherOrganization() throws Exception {
            Organization organization = organization();
            Organization other = organization();
            long workId = save(registered(organization, "대기 작업", Instant.now().minus(Duration.ofHours(1))));

            detail(other.owner(), other.id(), workId)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-001"));
        }

        @Test
        @DisplayName("배정된 적 없는 기사의 요청은 SCHEDULE-001로 숨긴다")
        void hidesWorkNeverAssignedToTechnician() throws Exception {
            Organization organization = organization();
            long workId = save(registered(organization, "대기 작업", Instant.now().minus(Duration.ofHours(1))));
            long technician = account();
            technicianContract(organization.id(), technician);

            detail(technician, organization.id(), workId)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-001"));
        }

        @Test
        @DisplayName("지금 담당인 기사는 일정·완료보고(사진 주소 포함)를 받고 등록자는 받지 않는다")
        void returnsDetailForCurrentTechnician() throws Exception {
            Organization organization = organization();
            givenDisplayInfos(organization);
            long technicianAccount = account();
            technicianContract(organization.id(), technicianAccount);
            TechnicianId technician = new TechnicianId(technicianIdOf(organization.id(), technicianAccount));
            Work work = registered(organization, "완료된 작업", Instant.now().minus(Duration.ofHours(3)));
            Instant started = Instant.now().minus(Duration.ofHours(2));
            work.assign(
                    new WorkSchedule(technician, started, Duration.ofHours(1)),
                    started.minusSeconds(60),
                    new MembershipId(organization.ownerMembership()));
            work.accept(started.minusSeconds(30));
            work.start(started);
            work.submitCompletionReport(
                    new CompletionReport(List.of("before-1"), List.of("after-1"), "배관 1개", "교체 완료", null, null),
                    started.plus(Duration.ofHours(1)));
            long workId = save(work);

            detail(technicianAccount, organization.id(), workId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.result.registrar").value(nullValue()))
                    .andExpect(jsonPath("$.result.schedule.startTime").exists())
                    .andExpect(jsonPath("$.result.completionReport.workNote").value("교체 완료"))
                    .andExpect(jsonPath("$.result.completionReport.usedParts").value("배관 1개"))
                    .andExpect(jsonPath("$.result.completionReport.beforePhotos[0].photoId")
                            .value("before-1"))
                    .andExpect(jsonPath("$.result.completionReport.afterPhotos[0].url")
                            .exists());
        }

        @Test
        @DisplayName("지금 담당이 아닌 기사는 작업명·상태와 자기 배정만 받고 나머지는 비어 있다")
        void returnsOwnAssignmentsForFormerTechnician() throws Exception {
            Organization organization = organization();
            long technicianAccount = account();
            technicianContract(organization.id(), technicianAccount);
            TechnicianId technician = new TechnicianId(technicianIdOf(organization.id(), technicianAccount));
            Work work = registered(organization, "넘어간 작업", Instant.now().minus(Duration.ofHours(3)));
            work.assign(
                    new WorkSchedule(technician, DAY, Duration.ofHours(1)),
                    Instant.now().minusSeconds(120),
                    new MembershipId(organization.ownerMembership()));
            work.reassign(
                    new WorkSchedule(TECHNICIAN, DAY, Duration.ofHours(1)),
                    Instant.now().minusSeconds(60),
                    new MembershipId(organization.ownerMembership()));
            long workId = save(work);

            detail(technicianAccount, organization.id(), workId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.name").value("넘어간 작업"))
                    .andExpect(jsonPath("$.result.delayed").value(false))
                    .andExpect(jsonPath("$.result.customer").value(nullValue()))
                    .andExpect(jsonPath("$.result.schedule").value(nullValue()))
                    .andExpect(jsonPath("$.result.registeredAt").value(nullValue()))
                    .andExpect(jsonPath("$.result.assignments.length()").value(1))
                    .andExpect(
                            jsonPath("$.result.assignments[0].assignmentNumber").value(1))
                    .andExpect(jsonPath("$.result.assignments[0].endReason").value("REASSIGNED"));
        }

        @Test
        @DisplayName("고정 경로인 대기함 요약은 작업 상세 경로와 겹치지 않는다")
        void keepsBacklogPathApartFromDetail() throws Exception {
            Organization organization = organization();

            mockMvc.perform(get("/api/v1/organizations/{organizationId}/works/backlog", organization.id())
                            .header(HttpHeaders.AUTHORIZATION, bearer(organization.owner())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.totalCount").value(0));
            detail(organization.owner(), organization.id(), 0)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-003"));
        }

        private ResultActions detail(long requester, long organizationId, long workId) throws Exception {
            return mockMvc.perform(get("/api/v1/organizations/{organizationId}/works/{workId}", organizationId, workId)
                    .header(HttpHeaders.AUTHORIZATION, bearer(requester)));
        }
    }

    @Nested
    @DisplayName("진행 보드")
    class ProgressBoard {

        @Test
        @DisplayName("구간의 작업을 등록부터 완료까지 상태별 열로 나눠 담당 기사·고객 이름·거절 표시와 함께 돌려주고, 진행 중은 작업중만 센다")
        void returnsBoard() throws Exception {
            Organization organization = organization();
            givenDisplayInfos(organization);
            Work pending = registered(organization, "배정된 작업", Instant.now().minus(Duration.ofHours(2)));
            pending.changeDetails(
                    "배정된 작업", new WorkTypeId(INSTALL.id()), new CustomerInfo("박서준", null, null), null, null);
            pending.assign(
                    new WorkSchedule(TECHNICIAN, DAY, Duration.ofHours(2)),
                    Instant.now().minusSeconds(60),
                    new MembershipId(organization.ownerMembership()));
            long pendingId = save(pending);
            save(registered(organization, "대기 작업", Instant.now().minus(Duration.ofHours(1))));
            Work rejected = registered(organization, "거절된 작업", Instant.now().minus(Duration.ofHours(1)));
            rejected.assign(
                    new WorkSchedule(TECHNICIAN, DAY.plus(Duration.ofHours(3)), Duration.ofHours(1)),
                    Instant.now().minusSeconds(120),
                    new MembershipId(organization.ownerMembership()));
            rejected.reject(
                    new Rejection(RejectionReason.SCHEDULE_CONFLICT, null),
                    Instant.now().minusSeconds(60));
            long rejectedId = save(rejected);

            board(organization.owner(), organization.id(), "from=" + DAY + "&to=" + DAY.plus(Duration.ofDays(1)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.columns.length()").value(5))
                    .andExpect(jsonPath("$.result.columns[0].status").value("REGISTERED"))
                    .andExpect(jsonPath("$.result.columns[0].cards.length()").value(1))
                    .andExpect(jsonPath("$.result.columns[0].cards[0].workId").value(rejectedId))
                    .andExpect(jsonPath("$.result.columns[0].cards[0].returnedByRejection")
                            .value(true))
                    .andExpect(jsonPath("$.result.columns[0].cards[0].technician.name")
                            .value("김민수"))
                    .andExpect(jsonPath("$.result.columns[1].status").value("PENDING_ACCEPTANCE"))
                    .andExpect(jsonPath("$.result.columns[1].cards[0].workId").value(pendingId))
                    .andExpect(jsonPath("$.result.columns[1].cards[0].technician.name")
                            .value("김민수"))
                    .andExpect(jsonPath("$.result.columns[1].cards[0].customerName")
                            .value("박서준"))
                    .andExpect(jsonPath("$.result.columns[1].cards[0].delayed").value(false))
                    .andExpect(
                            jsonPath("$.result.columns[1].cards[0].startTime").value(DAY.toString()))
                    .andExpect(jsonPath("$.result.columns[1].cards[0].endTime")
                            .value(DAY.plus(Duration.ofHours(2)).toString()))
                    .andExpect(jsonPath("$.result.summary.total").value(2))
                    .andExpect(jsonPath("$.result.summary.inProgress").value(0))
                    .andExpect(jsonPath("$.result.summary.delayed").value(0))
                    .andExpect(jsonPath("$.result.summary.completed").value(0));
        }

        @Test
        @DisplayName("구간이 없으면 SCHEDULE-003, 기사는 SCHEDULE-005다")
        void rejectsInvalidRequests() throws Exception {
            Organization organization = organization();
            long technician = account();
            technicianContract(organization.id(), technician);

            board(organization.owner(), organization.id(), "to=" + DAY)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-003"));
            board(technician, organization.id(), "from=" + DAY + "&to=" + DAY.plus(Duration.ofDays(1)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-005"));
        }

        private ResultActions board(long requester, long organizationId, String query) throws Exception {
            return mockMvc.perform(get("/api/v1/organizations/{organizationId}/progress-board?" + query, organizationId)
                    .header(HttpHeaders.AUTHORIZATION, bearer(requester)));
        }
    }

    private long technicianIdOf(long organizationId, long accountId) {
        return jdbcTemplate.queryForObject(
                "select id from technician where company_id = ? and member_id = ?",
                Long.class,
                organizationId,
                accountId);
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

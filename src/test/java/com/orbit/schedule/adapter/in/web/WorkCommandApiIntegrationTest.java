package com.orbit.schedule.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.orbit.schedule.domain.AssignmentEndReason;
import com.orbit.schedule.domain.CancellationReason;
import com.orbit.schedule.domain.CustomerInfo;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.Money;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.PaymentInfo;
import com.orbit.schedule.domain.PaymentMethod;
import com.orbit.schedule.domain.PlannedSchedule;
import com.orbit.schedule.domain.SiteAddress;
import com.orbit.schedule.domain.TechnicianId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkStatus;

/** 작업 변경 API를 공통 응답·보안·트랜잭션·영속성과 함께 확인한다. 준비물은 {@link ScheduleApiTestSupport}를 따른다. */
@DisplayName("작업 변경 API")
class WorkCommandApiIntegrationTest extends ScheduleApiTestSupport {

    private static final Instant REGISTERED_AT = Instant.parse("2020-01-01T00:00:00Z");

    private static final Instant START = Instant.parse("2030-03-02T01:00:00Z");

    @Nested
    @DisplayName("작업 등록")
    class Create {

        @Test
        @DisplayName("작업명만으로 등록하면 대기함에 두고 새 식별자를 돌려준다")
        void registersBacklogWork() throws Exception {
            Organization organization = organization();

            long workId = createdId(create(
                            organization.owner(),
                            organization.id(),
                            Map.of(
                                    "name",
                                    "에어컨 수리",
                                    "customer",
                                    Map.of("postalCode", "04043", "address", "서울시 마포구 양화로 45", "addressDetail", "3층")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.applied").value(true)));

            Work saved = stored(organization, workId);
            assertThat(saved.status()).isEqualTo(WorkStatus.REGISTERED);
            assertThat(saved.registrarId()).isEqualTo(new MembershipId(organization.ownerMembership()));
            assertThat(saved.customerInfo())
                    .isEqualTo(new CustomerInfo(null, null, new SiteAddress("04043", "서울시 마포구 양화로 45", "3층")));
        }

        @Test
        @DisplayName("현장 사진 식별자를 함께 주면 붙인 순서대로 남긴다")
        void registersWithSitePhotos() throws Exception {
            Organization organization = organization();

            long workId = createdId(create(
                            organization.owner(),
                            organization.id(),
                            Map.of("name", "에어컨 수리", "sitePhotoIds", List.of("photo-1", "photo-2")))
                    .andExpect(status().isOk()));

            assertThat(stored(organization, workId).sitePhotos()).containsExactly("photo-1", "photo-2");
        }

        @Test
        @DisplayName("현장 사진이 6장을 넘으면 400 SCHEDULE-003이고 만들지 않는다")
        void rejectsTooManySitePhotos() throws Exception {
            Organization organization = organization();

            create(
                            organization.owner(),
                            organization.id(),
                            Map.of("name", "에어컨 수리", "sitePhotoIds", Collections.nCopies(7, "photo")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-003"));
            assertThat(worksIn(organization)).isZero();
        }

        @Test
        @DisplayName("담당기사·일정을 함께 주면 등록과 함께 배정한다")
        void registersAndAssigns() throws Exception {
            Organization organization = organization();
            activeTechnician(organization, 12L);

            long workId = createdId(create(
                            organization.owner(),
                            organization.id(),
                            Map.of(
                                    "name", "에어컨 수리",
                                    "customer", Map.of("name", "이정호"),
                                    "schedule", assignment(12L, START, 90, false)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.applied").value(true)));

            Work saved = stored(organization, workId);
            assertThat(saved.status()).isEqualTo(WorkStatus.PENDING_ACCEPT);
            assertThat(saved.schedule())
                    .contains(new WorkSchedule(new TechnicianId(12L), START, Duration.ofMinutes(90)));
        }

        @Test
        @DisplayName("겹치는데 확인하지 않으면 작업을 만들지 않고 겹친 작업을 돌려주며, 확인하면 한 번만 만든다")
        void doesNotCreateUntilConflictConfirmed() throws Exception {
            Organization organization = organization();
            long existing = assignedWork(organization);
            activeTechnician(organization, 1L);
            Map<String, Object> unconfirmed = Map.of("name", "에어컨 수리", "schedule", assignment(1L, START, 60, false));

            create(organization.owner(), organization.id(), unconfirmed)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.applied").value(false))
                    .andExpect(jsonPath("$.result.workId").doesNotExist())
                    .andExpect(jsonPath("$.result.conflicts[0].workId").value(existing));
            assertThat(worksIn(organization)).isEqualTo(1);

            create(
                            organization.owner(),
                            organization.id(),
                            Map.of("name", "에어컨 수리", "schedule", assignment(1L, START, 60, true)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.applied").value(true))
                    .andExpect(jsonPath("$.result.workId").isNumber());
            assertThat(worksIn(organization)).isEqualTo(2);
        }

        @Test
        @DisplayName("일정 항목을 일부만 주면 입력한 값을 남긴 채 대기함에 둔다")
        void keepsPartialScheduleInBacklog() throws Exception {
            Organization organization = organization();
            activeTechnician(organization, 12L);

            long technicianOnly = createdId(create(
                            organization.owner(),
                            organization.id(),
                            Map.of("name", "에어컨 수리", "schedule", Map.of("technicianId", 12)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.applied").value(true)));
            long timeOnly = createdId(create(
                            organization.owner(),
                            organization.id(),
                            Map.of(
                                    "name",
                                    "보일러 점검",
                                    "schedule",
                                    Map.of("startTime", START.toString(), "expectedDurationMinutes", 60)))
                    .andExpect(status().isOk()));

            Work technicianWork = stored(organization, technicianOnly);
            assertThat(technicianWork.status()).isEqualTo(WorkStatus.REGISTERED);
            assertThat(technicianWork.plannedSchedule())
                    .contains(new PlannedSchedule(new TechnicianId(12L), null, null));
            assertThat(stored(organization, timeOnly).plannedSchedule())
                    .contains(new PlannedSchedule(null, START, Duration.ofHours(1)));
        }

        @Test
        @DisplayName("일부만 준 담당기사가 활성 기사 계약이 아니거나 작업명이 비면 400이고 만들지 않는다")
        void rejectsInactivePlannedTechnicianAndBlankName() throws Exception {
            Organization organization = organization();

            create(
                            organization.owner(),
                            organization.id(),
                            Map.of("name", "에어컨 수리", "schedule", Map.of("technicianId", 12)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-013"));
            create(organization.owner(), organization.id(), Map.of("name", " "))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-003"));
            assertThat(worksIn(organization)).isZero();
        }

        @Test
        @DisplayName("배정할 기사가 활성 기사 계약이 아니면 400 SCHEDULE-013이고 만들지 않는다")
        void rejectsTechnicianWithoutActiveContract() throws Exception {
            Organization organization = organization();

            create(
                            organization.owner(),
                            organization.id(),
                            Map.of("name", "에어컨 수리", "schedule", assignment(12L, START, 60, true)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-013"));
            assertThat(worksIn(organization)).isZero();
        }

        @Test
        @DisplayName("기사는 403 SCHEDULE-005다")
        void rejectsTechnician() throws Exception {
            Organization organization = organization();
            long technician = account();
            technicianContract(organization.id(), technician);

            create(technician, organization.id(), Map.of("name", "에어컨 수리"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-005"));
        }
    }

    @Nested
    @DisplayName("기본정보 수정")
    class UpdateDetails {

        @Test
        @DisplayName("총관리자가 기본정보를 교체하면 204이고, 비운 선택 항목은 지운다")
        void replacesDetails() throws Exception {
            Organization organization = organization();
            long workId = registeredWork(organization);

            update(
                            organization.owner(),
                            organization.id(),
                            workId,
                            Map.of(
                                    "name", "보일러 점검",
                                    "customer",
                                            Map.of(
                                                    "name",
                                                    "김철수",
                                                    "phone",
                                                    "010-9876-5432",
                                                    "postalCode",
                                                    "48058",
                                                    "address",
                                                    "부산시 해운대구 센텀중앙로 79",
                                                    "addressDetail",
                                                    "101동 1203호"),
                                    "payment", Map.of("fee", 80_000, "method", "INVOICE")))
                    .andExpect(status().isNoContent());

            Work saved = stored(organization, workId);
            assertThat(saved.name()).isEqualTo("보일러 점검");
            assertThat(saved.customerInfo())
                    .isEqualTo(new CustomerInfo(
                            "김철수", "010-9876-5432", new SiteAddress("48058", "부산시 해운대구 센텀중앙로 79", "101동 1203호")));
            assertThat(saved.paymentInfo()).isEqualTo(new PaymentInfo(new Money(80_000L), PaymentMethod.INVOICE));

            update(organization.owner(), organization.id(), workId, Map.of("name", "보일러 점검"))
                    .andExpect(status().isNoContent());
            assertThat(stored(organization, workId).customerInfo()).isEqualTo(new CustomerInfo(null, null, null));
        }

        @Test
        @DisplayName("작업명이 비면 400 SCHEDULE-003이다")
        void rejectsBlankName() throws Exception {
            Organization organization = organization();
            long workId = registeredWork(organization);

            update(organization.owner(), organization.id(), workId, Map.of("name", " "))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-003"));
        }

        @Test
        @DisplayName("현장 사진을 보낸 식별자로 교체하고, 6장을 넘으면 400 SCHEDULE-003이며 바꾸지 않는다")
        void replacesSitePhotos() throws Exception {
            Organization organization = organization();
            long workId = registeredWork(organization);

            update(
                            organization.owner(),
                            organization.id(),
                            workId,
                            Map.of("name", "보일러 점검", "sitePhotoIds", List.of("site-2", "site-1")))
                    .andExpect(status().isNoContent());
            assertThat(stored(organization, workId).sitePhotos()).containsExactly("site-2", "site-1");

            update(
                            organization.owner(),
                            organization.id(),
                            workId,
                            Map.of("name", "보일러 점검", "sitePhotoIds", Collections.nCopies(7, "site")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-003"));
            assertThat(stored(organization, workId).sitePhotos()).containsExactly("site-2", "site-1");

            update(organization.owner(), organization.id(), workId, Map.of("name", "보일러 점검"))
                    .andExpect(status().isNoContent());
            assertThat(stored(organization, workId).sitePhotos()).isEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.orbit.schedule.adapter.in.web.WorkCommandApiIntegrationTest#malformedAddresses")
        @DisplayName("우편번호가 숫자 5자리가 아니거나 기본주소 없이 상세주소만 보내면 400 SCHEDULE-003이다")
        void rejectsMalformedAddress(String name, Map<String, Object> customer) throws Exception {
            Organization organization = organization();
            long workId = registeredWork(organization);

            update(organization.owner(), organization.id(), workId, Map.of("name", "보일러 점검", "customer", customer))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-003"));
            assertThat(stored(organization, workId).name()).isEqualTo("대기 작업");
        }

        @Test
        @DisplayName("작업중 작업의 작업 유형을 바꾸면 유형 확인보다 먼저 409 SCHEDULE-002이고, 유형을 그대로 두면 다른 항목을 고친다")
        void keepsWorkTypeOfStartedWork() throws Exception {
            Organization organization = organization();
            Work work = registered(organization);
            work.assign(
                    new WorkSchedule(new TechnicianId(1L), START, Duration.ofHours(2)),
                    Instant.now().minusSeconds(120),
                    new MembershipId(organization.ownerMembership()));
            work.accept(Instant.now().minusSeconds(60));
            work.start(Instant.now().minusSeconds(30));
            long workId = save(work);

            update(organization.owner(), organization.id(), workId, Map.of("name", "보일러 점검", "workTypeId", 3))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-002"));
            update(organization.owner(), organization.id(), workId, Map.of("name", "보일러 점검"))
                    .andExpect(status().isNoContent());
            assertThat(stored(organization, workId).name()).isEqualTo("보일러 점검");
        }

        @Test
        @DisplayName("작업 유형을 지정하면 선택할 수 있는 유형이 아직 없어 400 SCHEDULE-014다")
        void rejectsWorkTypeUntilWorkTypesExist() throws Exception {
            Organization organization = organization();
            long workId = registeredWork(organization);

            update(organization.owner(), organization.id(), workId, Map.of("name", "보일러 점검", "workTypeId", 3))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-014"));
            assertThat(stored(organization, workId).name()).isEqualTo("대기 작업");
        }

        @Test
        @DisplayName("취소된 작업은 409 SCHEDULE-002다")
        void rejectsCancelledWork() throws Exception {
            Organization organization = organization();
            long workId = cancelledWork(organization);

            update(organization.owner(), organization.id(), workId, Map.of("name", "보일러 점검"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-002"));
        }

        @Test
        @DisplayName("다른 조직의 작업은 존재를 숨겨 404 SCHEDULE-001이다")
        void hidesWorkOfOtherOrganization() throws Exception {
            Organization organization = organization();
            long otherOrganizationWork = registeredWork(organization());

            update(organization.owner(), organization.id(), otherOrganizationWork, Map.of("name", "보일러 점검"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-001"));
        }

        @Test
        @DisplayName("그 조직의 구성원이 아니면 403 SCHEDULE-004, 기사면 403 SCHEDULE-005다")
        void rejectsNonMemberAndTechnician() throws Exception {
            Organization organization = organization();
            long workId = registeredWork(organization);
            long technician = account();
            technicianContract(organization.id(), technician);

            update(account(), organization.id(), workId, Map.of("name", "보일러 점검"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-004"));
            update(technician, organization.id(), workId, Map.of("name", "보일러 점검"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-005"));
        }

        @Test
        @DisplayName("등록자가 아닌 직원이면 403 SCHEDULE-005이고 바꾸지 않으며, 등록자 본인인 직원은 수정한다")
        void allowsOnlyRegistrarAmongStaff() throws Exception {
            Organization organization = organization();
            long workId = registeredWork(organization);
            long staff = account();
            staffMembership(organization.id(), staff);

            update(staff, organization.id(), workId, Map.of("name", "보일러 점검"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-005"));
            assertThat(stored(organization, workId).name()).isEqualTo("대기 작업");

            long ownWorkId = createdId(
                    create(staff, organization.id(), Map.of("name", "직원 작업")).andExpect(status().isOk()));
            update(staff, organization.id(), ownWorkId, Map.of("name", "보일러 점검"))
                    .andExpect(status().isNoContent());
            assertThat(stored(organization, ownWorkId).name()).isEqualTo("보일러 점검");
        }

        @Test
        @DisplayName("본문 형식이 깨졌거나 목록에 없는 결제 방식이면 400 COMMON-400이다")
        void rejectsMalformedBody() throws Exception {
            Organization organization = organization();
            long workId = registeredWork(organization);

            mockMvc.perform(put(WORK, organization.id(), workId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(organization.owner()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMMON-400"));
            update(
                            organization.owner(),
                            organization.id(),
                            workId,
                            Map.of("name", "보일러 점검", "payment", Map.of("method", "CHEQUE")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMMON-400"));
        }
    }

    @Nested
    @DisplayName("작업 취소")
    class Cancel {

        @Test
        @DisplayName("등록자 본인이 배정된 작업을 취소하면 204이고 사유와 함께 취소로 남는다")
        void cancelsAssignedWork() throws Exception {
            Organization organization = organization();
            long workId = assignedWork(organization);

            cancel(organization.owner(), organization.id(), workId, Map.of("reason", "CUSTOMER_REQUEST"))
                    .andExpect(status().isNoContent());

            Work saved = stored(organization, workId);
            assertThat(saved.status()).isEqualTo(WorkStatus.CANCELED);
            assertThat(saved.cancellation()).hasValueSatisfying(cancellation -> {
                assertThat(cancellation.reason()).isEqualTo(CancellationReason.CUSTOMER_REQUEST);
                assertThat(cancellation.note()).isNull();
            });
        }

        @Test
        @DisplayName("등록자가 아닌 직원이 취소하면 403 SCHEDULE-005이고 취소하지 않는다")
        void rejectsStaffWhoDidNotRegister() throws Exception {
            Organization organization = organization();
            long staff = account();
            staffMembership(organization.id(), staff);
            long workId = assignedWork(organization);

            cancel(staff, organization.id(), workId, Map.of("reason", "CUSTOMER_REQUEST"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-005"));
            assertThat(stored(organization, workId).status()).isEqualTo(WorkStatus.PENDING_ACCEPT);
        }

        @Test
        @DisplayName("기타 사유는 메모를 함께 남긴다")
        void keepsNoteOfOtherReason() throws Exception {
            Organization organization = organization();
            long workId = registeredWork(organization);

            cancel(organization.owner(), organization.id(), workId, Map.of("reason", "OTHER", "note", "고객 연락 두절"))
                    .andExpect(status().isNoContent());

            assertThat(stored(organization, workId).cancellation())
                    .hasValueSatisfying(
                            cancellation -> assertThat(cancellation.note()).isEqualTo("고객 연락 두절"));
        }

        @Test
        @DisplayName("사유가 없거나 기타인데 메모가 없으면 400 SCHEDULE-003이다")
        void rejectsMissingReasonOrNote() throws Exception {
            Organization organization = organization();
            long workId = registeredWork(organization);

            cancel(organization.owner(), organization.id(), workId, Map.of())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-003"));
            cancel(organization.owner(), organization.id(), workId, Map.of("reason", "OTHER"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-003"));
            assertThat(stored(organization, workId).status()).isEqualTo(WorkStatus.REGISTERED);
        }

        @Test
        @DisplayName("목록에 없는 사유는 400 COMMON-400이다")
        void rejectsUnknownReason() throws Exception {
            Organization organization = organization();
            long workId = registeredWork(organization);

            cancel(organization.owner(), organization.id(), workId, Map.of("reason", "WEATHER"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMMON-400"));
        }

        @Test
        @DisplayName("이미 취소된 작업은 409 SCHEDULE-002다")
        void rejectsAlreadyCancelledWork() throws Exception {
            Organization organization = organization();
            long workId = cancelledWork(organization);

            cancel(organization.owner(), organization.id(), workId, Map.of("reason", "CUSTOMER_REQUEST"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-002"));
        }

        @Test
        @DisplayName("그 조직의 구성원이 아니면 작업·입력과 관계없이 403 SCHEDULE-004다")
        void rejectsNonMember() throws Exception {
            Organization organization = organization();
            long workId = registeredWork(organization);
            long outsider = account();

            cancel(outsider, organization.id(), workId, Map.of())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-004"));
        }

        @Test
        @DisplayName("기사는 403 SCHEDULE-005다")
        void rejectsTechnician() throws Exception {
            Organization organization = organization();
            long workId = registeredWork(organization);
            long technician = account();
            technicianContract(organization.id(), technician);

            cancel(technician, organization.id(), workId, Map.of("reason", "CUSTOMER_REQUEST"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-005"));
        }

        @Test
        @DisplayName("토큰이 없으면 401이다")
        void requiresAuthentication() throws Exception {
            Organization organization = organization();
            long workId = registeredWork(organization);

            mockMvc.perform(post(CANCELLATION, organization.id(), workId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"CUSTOMER_REQUEST\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("COMMON-401"));
        }
    }

    @Nested
    @DisplayName("배정 변경")
    class ChangeAssignment {

        @Test
        @DisplayName("대기함 작업을 기사·시간에 놓으면 배정되고 200 applied=true다")
        void assignsBacklogWork() throws Exception {
            Organization organization = organization();
            long workId = registeredWork(organization);
            activeTechnician(organization, 12L);

            change(organization.owner(), organization.id(), workId, assignment(12L, START, 120, false))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.applied").value(true))
                    .andExpect(jsonPath("$.result.conflicts").isEmpty());

            Work saved = stored(organization, workId);
            assertThat(saved.status()).isEqualTo(WorkStatus.PENDING_ACCEPT);
            assertThat(saved.schedule()).contains(new WorkSchedule(new TechnicianId(12L), START, Duration.ofHours(2)));
        }

        @Test
        @DisplayName("대기함 작업은 등록자가 아닌 직원도 배정하지만, 배정된 작업을 옮기거나 해제하면 403 SCHEDULE-005다")
        void letsStaffAssignBacklogOnly() throws Exception {
            Organization organization = organization();
            long workId = registeredWork(organization);
            long staff = account();
            staffMembership(organization.id(), staff);
            activeTechnician(organization, 12L);

            change(staff, organization.id(), workId, assignment(12L, START, 120, false))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.applied").value(true));

            change(staff, organization.id(), workId, assignment(12L, START.plus(Duration.ofHours(3)), 120, false))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-005"));
            unassign(staff, organization.id(), workId)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-005"));
            assertThat(stored(organization, workId).schedule())
                    .contains(new WorkSchedule(new TechnicianId(12L), START, Duration.ofHours(2)));
        }

        @Test
        @DisplayName("화면을 본 뒤 다른 사람이 바꿔 버전이 다르면 409 SCHEDULE-016이고 반영하지 않으며, 버전이 없으면 400 SCHEDULE-003이다")
        void rejectsStaleOrMissingVersion() throws Exception {
            Organization organization = organization();
            long workId = registeredWork(organization);
            activeTechnician(organization, 12L);
            change(organization.owner(), organization.id(), workId, assignment(12L, START, 120, false))
                    .andExpect(status().isOk());
            assertThat(currentVersion(workId)).as("반영하면 버전이 오른다").isEqualTo(1L);

            Map<String, Object> stale = new HashMap<>(assignment(12L, START.plus(Duration.ofHours(3)), 120, false));
            stale.put("expectedVersion", 0);
            change(organization.owner(), organization.id(), workId, stale)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-016"));
            Map<String, Object> missing = new HashMap<>(assignment(12L, START.plus(Duration.ofHours(3)), 120, false));
            missing.put("expectedVersion", null);
            change(organization.owner(), organization.id(), workId, missing)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-003"));
            assertThat(stored(organization, workId).schedule())
                    .contains(new WorkSchedule(new TechnicianId(12L), START, Duration.ofHours(2)));
        }

        @Test
        @DisplayName("같은 기사에서 시간만 옮기면 일정 변경이다")
        void reschedulesForSameTechnician() throws Exception {
            Organization organization = organization();
            long workId = assignedWork(organization);
            Instant later = START.plus(Duration.ofHours(3));

            change(organization.owner(), organization.id(), workId, assignment(1L, later, 60, false))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.applied").value(true));

            assertThat(stored(organization, workId).schedule())
                    .contains(new WorkSchedule(new TechnicianId(1L), later, Duration.ofHours(1)));
        }

        @Test
        @DisplayName("다른 기사로 옮기면 재배정되어 이전 배정은 재배정으로 끝나고 새 배정 이력이 쌓인다")
        void reassignsToOtherTechnician() throws Exception {
            Organization organization = organization();
            long workId = assignedWork(organization);
            activeTechnician(organization, 12L);

            change(organization.owner(), organization.id(), workId, assignment(12L, START, 120, false))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.applied").value(true));

            Work saved = stored(organization, workId);
            assertThat(saved.status()).isEqualTo(WorkStatus.PENDING_ACCEPT);
            assertThat(saved.schedule()).contains(new WorkSchedule(new TechnicianId(12L), START, Duration.ofHours(2)));
            assertThat(saved.assignmentHistory()).hasSize(2);
            assertThat(saved.assignmentHistory().getFirst().ending())
                    .hasValueSatisfying(
                            ending -> assertThat(ending.reason()).isEqualTo(AssignmentEndReason.REASSIGNED));
        }

        @Test
        @DisplayName("취소된 작업은 409 SCHEDULE-002, 다른 조직의 작업은 404 SCHEDULE-001이다")
        void rejectsCancelledAndOtherOrganizationWork() throws Exception {
            Organization organization = organization();
            long cancelled = cancelledWork(organization);
            long otherOrganizationWork = registeredWork(organization());

            change(organization.owner(), organization.id(), cancelled, assignment(1L, START, 60, false))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-002"));
            change(organization.owner(), organization.id(), otherOrganizationWork, assignment(1L, START, 60, false))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-001"));
        }

        @Test
        @DisplayName("그 조직의 구성원이 아니면 403 SCHEDULE-004, 기사면 403 SCHEDULE-005다")
        void rejectsNonMemberAndTechnician() throws Exception {
            Organization organization = organization();
            long workId = registeredWork(organization);
            long technician = account();
            technicianContract(organization.id(), technician);

            change(account(), organization.id(), workId, assignment(1L, START, 60, false))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-004"));
            change(technician, organization.id(), workId, assignment(1L, START, 60, false))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-005"));
        }

        @Test
        @DisplayName("같은 기사의 다른 작업과 겹치면 확인 전에는 반영하지 않고 겹친 작업을 돌려주며, 확인하면 반영한다")
        void withholdsConflictUntilConfirmed() throws Exception {
            Organization organization = organization();
            long existing = assignedWork(organization);
            long target = registeredWork(organization);
            activeTechnician(organization, 1L);

            change(
                            organization.owner(),
                            organization.id(),
                            target,
                            assignment(1L, START.plus(Duration.ofHours(1)), 120, false))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.applied").value(false))
                    .andExpect(jsonPath("$.result.conflicts[0].workId").value(existing));
            assertThat(stored(organization, target).status()).isEqualTo(WorkStatus.REGISTERED);

            change(
                            organization.owner(),
                            organization.id(),
                            target,
                            assignment(1L, START.plus(Duration.ofHours(1)), 120, true))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.applied").value(true))
                    .andExpect(jsonPath("$.result.conflicts[0].workId").value(existing));
            assertThat(stored(organization, target).status()).isEqualTo(WorkStatus.PENDING_ACCEPT);
        }

        @Test
        @DisplayName("배정할 기사가 그 조직의 활성 기사 계약이 아니면 400 SCHEDULE-013이다")
        void rejectsTechnicianWithoutActiveContract() throws Exception {
            Organization organization = organization();
            long workId = registeredWork(organization);

            change(organization.owner(), organization.id(), workId, assignment(12L, START, 120, true))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-013"));
            assertThat(stored(organization, workId).status()).isEqualTo(WorkStatus.REGISTERED);
        }

        @Test
        @DisplayName("같은 기사·같은 시간이면 409 SCHEDULE-008이다")
        void rejectsUnchangedSchedule() throws Exception {
            Organization organization = organization();
            long workId = assignedWork(organization);

            change(organization.owner(), organization.id(), workId, assignment(1L, START, 120, false))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-008"));
        }

        @Test
        @DisplayName("소요시간이 없거나 0이면 400 SCHEDULE-003이다")
        void rejectsInvalidDuration() throws Exception {
            Organization organization = organization();
            long workId = registeredWork(organization);

            change(
                            organization.owner(),
                            organization.id(),
                            workId,
                            Map.of("technicianId", 12, "startTime", START.toString()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-003"));
            change(organization.owner(), organization.id(), workId, assignment(12L, START, 0, false))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-003"));
        }
    }

    @Nested
    @DisplayName("배정 해제")
    class Unassign {

        @Test
        @DisplayName("버전이 없으면 400 SCHEDULE-003, 화면을 본 뒤 다른 사람이 바꿔 버전이 다르면 409 SCHEDULE-016이고 해제하지 않는다")
        void rejectsStaleOrMissingVersion() throws Exception {
            Organization organization = organization();
            long workId = assignedWork(organization);

            unassign(organization.owner(), organization.id(), workId, null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-003"));
            unassign(organization.owner(), organization.id(), workId, 1L)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-016"));
            assertThat(stored(organization, workId).status()).isEqualTo(WorkStatus.PENDING_ACCEPT);
        }

        @Test
        @DisplayName("배정된 작업의 배정을 풀면 204이고 대기함으로 돌아간다")
        void returnsWorkToBacklog() throws Exception {
            Organization organization = organization();
            long workId = assignedWork(organization);

            unassign(organization.owner(), organization.id(), workId).andExpect(status().isNoContent());

            Work saved = stored(organization, workId);
            assertThat(saved.status()).isEqualTo(WorkStatus.REGISTERED);
            assertThat(saved.schedule()).isEmpty();
        }

        @Test
        @DisplayName("대기함 작업은 풀 배정이 없어 409 SCHEDULE-002다")
        void rejectsBacklogWork() throws Exception {
            Organization organization = organization();
            long workId = registeredWork(organization);

            unassign(organization.owner(), organization.id(), workId)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-002"));
        }

        @Test
        @DisplayName("그 조직의 구성원이 아니면 403 SCHEDULE-004, 기사면 403 SCHEDULE-005다")
        void rejectsNonMemberAndTechnician() throws Exception {
            Organization organization = organization();
            long workId = assignedWork(organization);
            long technician = account();
            technicianContract(organization.id(), technician);

            unassign(account(), organization.id(), workId)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-004"));
            unassign(technician, organization.id(), workId)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-005"));
            assertThat(stored(organization, workId).status()).isEqualTo(WorkStatus.PENDING_ACCEPT);
        }

        @Test
        @DisplayName("작업 식별자 형식이 틀리면 400 SCHEDULE-003, 숫자가 아니면 400 COMMON-400이다")
        void rejectsInvalidWorkId() throws Exception {
            Organization organization = organization();

            unassign(organization.owner(), organization.id(), 0L)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-003"));
            mockMvc.perform(delete(ASSIGNMENT, organization.id(), "abc")
                            .header(HttpHeaders.AUTHORIZATION, bearer(organization.owner())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMMON-400"));
        }
    }

    private static final String WORKS = "/api/v1/organizations/{organizationId}/works";
    private static final String WORK = WORKS + "/{workId}";
    private static final String CANCELLATION = WORK + "/cancellation";
    private static final String ASSIGNMENT = WORK + "/assignment";

    private ResultActions create(long requester, long organizationId, Map<String, ?> body) throws Exception {
        return mockMvc.perform(post(WORKS, organizationId)
                .header(HttpHeaders.AUTHORIZATION, bearer(requester))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private long createdId(ResultActions result) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("result").path("workId").asLong();
    }

    private int worksIn(Organization organization) {
        return jdbcTemplate.queryForObject(
                "select count(*) from work where company_id = ?", Integer.class, organization.id());
    }

    private ResultActions update(long requester, long organizationId, long workId, Map<String, ?> body)
            throws Exception {
        return mockMvc.perform(put(WORK, organizationId, workId)
                .header(HttpHeaders.AUTHORIZATION, bearer(requester))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions cancel(long requester, long organizationId, long workId, Map<String, ?> body)
            throws Exception {
        return mockMvc.perform(post(CANCELLATION, organizationId, workId)
                .header(HttpHeaders.AUTHORIZATION, bearer(requester))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    /** 요청자가 지금 버전을 본 것으로 보낸다. 본문에 expectedVersion이 있으면 그대로 쓴다. */
    private ResultActions change(long requester, long organizationId, long workId, Map<String, ?> body)
            throws Exception {
        Map<String, Object> versioned = new HashMap<>(body);
        if (!body.containsKey("expectedVersion")) {
            versioned.put("expectedVersion", currentVersion(workId));
        }
        return mockMvc.perform(put(ASSIGNMENT, organizationId, workId)
                .header(HttpHeaders.AUTHORIZATION, bearer(requester))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(versioned)));
    }

    /** 저장된 작업 버전. 없는 작업이면 0이다(버전보다 앞선 오류를 보는 요청용). */
    private long currentVersion(long workId) {
        return jdbcTemplate.query(
                "select version from work where id = ?", rs -> rs.next() ? rs.getLong(1) : 0L, workId);
    }

    private static Map<String, Object> assignment(long technicianId, Instant start, int minutes, boolean confirmed) {
        return Map.of(
                "technicianId", technicianId,
                "startTime", start.toString(),
                "expectedDurationMinutes", minutes,
                "conflictConfirmed", confirmed);
    }

    private void activeTechnician(Organization organization, long technicianId) {
        given(technicianContractPort.isActive(new OrganizationId(organization.id()), new TechnicianId(technicianId)))
                .willReturn(true);
    }

    /** 요청자가 지금 버전을 본 것으로 보낸다. */
    private ResultActions unassign(long requester, long organizationId, long workId) throws Exception {
        return unassign(requester, organizationId, workId, currentVersion(workId));
    }

    private ResultActions unassign(long requester, long organizationId, long workId, Long expectedVersion)
            throws Exception {
        MockHttpServletRequestBuilder request =
                delete(ASSIGNMENT, organizationId, workId).header(HttpHeaders.AUTHORIZATION, bearer(requester));
        if (expectedVersion != null) {
            request.param("expectedVersion", expectedVersion.toString());
        }
        return mockMvc.perform(request);
    }

    private long registeredWork(Organization organization) {
        return save(registered(organization));
    }

    private long assignedWork(Organization organization) {
        Work work = registered(organization);
        work.assign(
                new WorkSchedule(new TechnicianId(1L), START, Duration.ofHours(2)),
                Instant.now().minusSeconds(60),
                new MembershipId(organization.ownerMembership()));
        return save(work);
    }

    private long cancelledWork(Organization organization) {
        Work work = registered(organization);
        work.cancel(
                Instant.now().minusSeconds(60),
                new MembershipId(organization.ownerMembership()),
                CancellationReason.DUPLICATE_REGISTRATION,
                null);
        return save(work);
    }

    private Work registered(Organization organization) {
        return Work.register(
                new OrganizationId(organization.id()),
                "대기 작업",
                new MembershipId(organization.ownerMembership()),
                REGISTERED_AT,
                null,
                new CustomerInfo("홍길동", "010-1234-5678", new SiteAddress(null, "서울시", null)),
                new PaymentInfo(new Money(150_000L), PaymentMethod.ON_SITE_CARD));
    }

    private Work stored(Organization organization, long workId) {
        return workRepository
                .findInOrganization(new OrganizationId(organization.id()), new WorkId(workId))
                .orElseThrow();
    }

    static Stream<Arguments> malformedAddresses() {
        return Stream.of(
                Arguments.of("우편번호 4자리", Map.of("postalCode", "4805", "address", "부산시")),
                Arguments.of("우편번호에 문자", Map.of("postalCode", "48O58", "address", "부산시")),
                Arguments.of("상세주소만", Map.of("addressDetail", "101동 1203호")));
    }
}

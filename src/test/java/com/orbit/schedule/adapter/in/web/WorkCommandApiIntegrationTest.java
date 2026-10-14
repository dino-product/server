package com.orbit.schedule.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.orbit.schedule.application.port.out.TechnicianContractPort;
import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.domain.CancellationReason;
import com.orbit.schedule.domain.CustomerInfo;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.Money;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.PaymentInfo;
import com.orbit.schedule.domain.PaymentMethod;
import com.orbit.schedule.domain.TechnicianId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkStatus;
import com.orbit.support.IntegrationTestSupport;

import tools.jackson.databind.ObjectMapper;

/**
 * 작업 변경 API를 공통 응답·보안·트랜잭션·영속성과 함께 확인한다. 발주사는 API로 만들고(생성자가 총관리자), 다른 직원 소속·기사 계약은 참여 승인 기능이 아직 없어
 * 테이블에 직접 넣는다. 작업은 등록 API가 생기기 전이라 저장소로 준비한다.
 */
@AutoConfigureMockMvc
@DisplayName("작업 변경 API")
class WorkCommandApiIntegrationTest extends IntegrationTestSupport {

    private static final AtomicLong ACCOUNT_IDS = new AtomicLong(70_000);
    private static final Instant START = Instant.parse("2030-03-02T01:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WorkRepository workRepository;

    // 배정할 기사의 계약 확인. organization 계약 전이라 대체하며, 넣지 않으면 임시 어댑터처럼 거부한다.
    @MockitoBean
    private TechnicianContractPort technicianContractPort;

    @Value("${app.auth.jwt.secret}")
    private String jwtSecret;

    @Value("${app.auth.jwt.issuer}")
    private String jwtIssuer;

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
                                    "customer", Map.of("name", "김철수", "phone", "010-9876-5432", "address", "부산시"),
                                    "payment", Map.of("fee", 80_000, "method", "BANK_TRANSFER")))
                    .andExpect(status().isNoContent());

            Work saved = stored(organization, workId);
            assertThat(saved.name()).isEqualTo("보일러 점검");
            assertThat(saved.customerInfo()).isEqualTo(new CustomerInfo("김철수", "010-9876-5432", "부산시"));
            assertThat(saved.paymentInfo()).isEqualTo(new PaymentInfo(new Money(80_000L), PaymentMethod.BANK_TRANSFER));

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
        @DisplayName("직원이 배정된 작업을 취소하면 204이고 사유와 함께 취소로 남는다")
        void cancelsAssignedWork() throws Exception {
            Organization organization = organization();
            long staff = account();
            staffMembership(organization.id(), staff);
            long workId = assignedWork(organization);

            cancel(staff, organization.id(), workId, Map.of("reason", "CUSTOMER_REQUEST"))
                    .andExpect(status().isNoContent());

            Work saved = stored(organization, workId);
            assertThat(saved.status()).isEqualTo(WorkStatus.CANCELLED);
            assertThat(saved.cancellation()).hasValueSatisfying(cancellation -> {
                assertThat(cancellation.reason()).isEqualTo(CancellationReason.CUSTOMER_REQUEST);
                assertThat(cancellation.note()).isNull();
            });
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
            assertThat(saved.status()).isEqualTo(WorkStatus.PENDING_ACCEPTANCE);
            assertThat(saved.schedule()).contains(new WorkSchedule(new TechnicianId(12L), START, Duration.ofHours(2)));
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
            assertThat(stored(organization, target).status()).isEqualTo(WorkStatus.PENDING_ACCEPTANCE);
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

    private static final String WORK = "/api/v1/organizations/{organizationId}/works/{workId}";
    private static final String CANCELLATION = WORK + "/cancellation";
    private static final String ASSIGNMENT = WORK + "/assignment";

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

    private ResultActions change(long requester, long organizationId, long workId, Map<String, ?> body)
            throws Exception {
        return mockMvc.perform(put(ASSIGNMENT, organizationId, workId)
                .header(HttpHeaders.AUTHORIZATION, bearer(requester))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
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

    private ResultActions unassign(long requester, long organizationId, long workId) throws Exception {
        return mockMvc.perform(
                delete(ASSIGNMENT, organizationId, workId).header(HttpHeaders.AUTHORIZATION, bearer(requester)));
    }

    /** 발주사와 그 총관리자. 총관리자의 직원 소속은 작업 등록자로도 쓴다. */
    private record Organization(long id, long owner, long ownerMembership) {}

    private Organization organization() throws Exception {
        long owner = account();
        String body = mockMvc.perform(post("/api/v1/organizations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "오르빗 설비", "industry", "HVAC"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long organizationId = objectMapper
                .readTree(body)
                .path("result")
                .path("organizationId")
                .asLong();
        Long membership = jdbcTemplate.queryForObject(
                "select id from company_memberships where company_id = ? and member_id = ?",
                Long.class,
                organizationId,
                owner);
        return new Organization(organizationId, owner, membership);
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
                null,
                new CustomerInfo("홍길동", "010-1234-5678", "서울시"),
                new PaymentInfo(new Money(150_000L), PaymentMethod.ON_SITE_CARD));
    }

    private long save(Work work) {
        return workRepository.save(work).id().orElseThrow().value();
    }

    private Work stored(Organization organization, long workId) {
        return workRepository
                .findInOrganization(new OrganizationId(organization.id()), new WorkId(workId))
                .orElseThrow();
    }

    private void staffMembership(long organizationId, long accountId) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "insert into company_memberships"
                        + " (company_id, member_id, is_owner, status,"
                        + " joined_at, status_changed_at, created_at, updated_at)"
                        + " values (?, ?, false, 'ACTIVE', ?, ?, ?, ?)",
                organizationId,
                accountId,
                now,
                now,
                now,
                now);
    }

    private void technicianContract(long organizationId, long accountId) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "insert into technician (company_id, member_id, status, status_changed_at, created_at, updated_at)"
                        + " values (?, ?, 'ACTIVE', ?, ?, ?)",
                organizationId,
                accountId,
                now,
                now,
                now);
    }

    private static long account() {
        return ACCOUNT_IDS.incrementAndGet();
    }

    private String bearer(long accountId) throws Exception {
        Instant issuedAt = Instant.now();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader(JWSAlgorithm.HS256),
                new JWTClaimsSet.Builder()
                        .issuer(jwtIssuer)
                        .subject(Long.toString(accountId))
                        .jwtID(UUID.randomUUID().toString())
                        .issueTime(Date.from(issuedAt))
                        .expirationTime(Date.from(issuedAt.plusSeconds(600)))
                        .claim("token_use", "access")
                        .build());
        jwt.sign(new MACSigner(new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        return "Bearer " + jwt.serialize();
    }
}

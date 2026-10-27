package com.orbit.schedule.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.TechnicianId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkSchedule;

import tools.jackson.databind.JsonNode;

/** 사진 업로드 주소 발급 API. 서명은 저장소를 부르지 않으므로 기본 테스트 저장소 주소로 확인한다. 실제 업로드는 {@link WorkPhotoFlowIntegrationTest}. */
@DisplayName("사진 업로드 주소 발급 API")
class PhotoUploadApiIntegrationTest extends ScheduleApiTestSupport {

    private static final long FORTY_MB = 40L * 1024 * 1024;

    @Nested
    @DisplayName("현장 사진")
    class Site {

        @Test
        @DisplayName("총관리자는 incoming 키에 형식·크기를 서명한 주소와 붙일 헤더·만료 시각을 받고, 업로드 대기 기록이 남는다")
        void ownerReceivesSignedUpload() throws Exception {
            Organization organization = organization();

            JsonNode result = result(site(organization.owner(), organization.id(), "image/jpeg", 2_457_600L)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true)));

            String fileId = result.path("fileId").asString();
            assertThat(UUID.fromString(fileId).toString()).isEqualTo(fileId);
            String url = result.path("uploadUrl").asString();
            assertThat(url)
                    .contains("/incoming/" + fileId)
                    .contains("X-Amz-SignedHeaders=content-length%3Bcontent-type%3Bhost");
            assertThat(result.path("requiredHeaders").path("content-type").asString())
                    .isEqualTo("image/jpeg");
            assertThat(result.path("requiredHeaders").path("content-length").asString())
                    .isEqualTo("2457600");
            assertThat(Instant.parse(result.path("expiresAt").asString()))
                    .isBetween(
                            Instant.now().plus(Duration.ofMinutes(9)),
                            Instant.now().plus(Duration.ofMinutes(11)));
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "select organization_id, uploader_account_id, purpose, status, size_bytes"
                            + " from uploaded_files where id = ?",
                    UUID.fromString(fileId));
            assertThat(row)
                    .containsEntry("organization_id", organization.id())
                    .containsEntry("uploader_account_id", organization.owner())
                    .containsEntry("purpose", "WORK_SITE_PHOTO")
                    .containsEntry("status", "PENDING")
                    .containsEntry("size_bytes", 2_457_600L);
        }

        @Test
        @DisplayName("이미지가 아니면 400 SCHEDULE-020, 형식·크기가 없으면 400 SCHEDULE-003이다")
        void rejectsInvalidInput() throws Exception {
            Organization organization = organization();

            site(organization.owner(), organization.id(), "application/pdf", 100L)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-020"));
            site(organization.owner(), organization.id(), "image/jpeg", null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-003"));
        }

        @Test
        @DisplayName("기사는 403 SCHEDULE-005, 구성원이 아니면 403 SCHEDULE-004, 인증이 없으면 401이다")
        void rejectsNonManagers() throws Exception {
            Organization organization = organization();
            long technician = account();
            technicianContract(organization.id(), technician);

            site(technician, organization.id(), "image/jpeg", 100L)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-005"));
            site(account(), organization.id(), "image/jpeg", 100L)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-004"));
            mockMvc.perform(post("/api/v1/organizations/{organizationId}/site-photo-uploads", organization.id())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"contentType\":\"image/jpeg\",\"sizeBytes\":1}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("완료보고 사진")
    class Completion {

        @Test
        @DisplayName("담당 기사는 작업중 작업에 발급받고, 40MB를 넘으면 400 SCHEDULE-021이다")
        void technicianReceivesUpload() throws Exception {
            Organization organization = organization();
            long technician = account();
            technicianContract(organization.id(), technician);
            long workId = save(inProgress(organization, technicianIdOf(organization.id(), technician)));

            JsonNode result = result(completion(technician, organization.id(), workId, "image/heic", FORTY_MB)
                    .andExpect(status().isOk()));
            assertThat(jdbcTemplate.queryForObject(
                            "select purpose from uploaded_files where id = ?",
                            String.class,
                            UUID.fromString(result.path("fileId").asString())))
                    .isEqualTo("WORK_COMPLETION_PHOTO");

            completion(technician, organization.id(), workId, "image/jpeg", FORTY_MB + 1)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-021"));
        }

        @Test
        @DisplayName("작업중이 아니면 409 SCHEDULE-002이고 업로드 대기 기록을 남기지 않으며, 이미지가 아니면 상태보다 400 SCHEDULE-020이 먼저다")
        void requiresInProgressAfterInput() throws Exception {
            Organization organization = organization();
            long technician = account();
            technicianContract(organization.id(), technician);
            long workId = save(accepted(organization, technicianIdOf(organization.id(), technician)));
            long filesBefore = jdbcTemplate.queryForObject("select count(*) from uploaded_files", Long.class);

            completion(technician, organization.id(), workId, "image/jpeg", 100L)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-002"));
            completion(technician, organization.id(), workId, "application/pdf", 100L)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-020"));
            assertThat(jdbcTemplate.queryForObject("select count(*) from uploaded_files", Long.class))
                    .isEqualTo(filesBefore);
        }

        @Test
        @DisplayName("관리자는 403 SCHEDULE-005, 다른 기사는 404 SCHEDULE-001이다")
        void rejectsOthers() throws Exception {
            Organization organization = organization();
            long technician = account();
            technicianContract(organization.id(), technician);
            long other = account();
            technicianContract(organization.id(), other);
            long workId = save(inProgress(organization, technicianIdOf(organization.id(), technician)));

            completion(organization.owner(), organization.id(), workId, "image/jpeg", 100L)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-005"));
            completion(other, organization.id(), workId, "image/jpeg", 100L)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SCHEDULE-001"));
        }
    }

    private ResultActions site(long account, long organizationId, String contentType, Long size) throws Exception {
        return mockMvc.perform(post("/api/v1/organizations/{organizationId}/site-photo-uploads", organizationId)
                .header(HttpHeaders.AUTHORIZATION, bearer(account))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body(contentType, size))));
    }

    private ResultActions completion(long account, long organizationId, long workId, String contentType, Long size)
            throws Exception {
        return mockMvc.perform(post(
                        "/api/v1/organizations/{organizationId}/works/{workId}/completion-photo-uploads",
                        organizationId,
                        workId)
                .header(HttpHeaders.AUTHORIZATION, bearer(account))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body(contentType, size))));
    }

    private static Map<String, Object> body(String contentType, Long size) {
        Map<String, Object> body = new HashMap<>();
        body.put("contentType", contentType);
        body.put("sizeBytes", size);
        return body;
    }

    private JsonNode result(ResultActions actions) throws Exception {
        return objectMapper
                .readTree(actions.andReturn().getResponse().getContentAsString())
                .path("result");
    }

    private long technicianIdOf(long organizationId, long accountId) {
        return jdbcTemplate.queryForObject(
                "select id from technician where company_id = ? and member_id = ?",
                Long.class,
                organizationId,
                accountId);
    }

    /** 지금 시작한 작업중 작업. */
    static Work inProgress(Organization organization, long technicianId) {
        Instant start = Instant.now().minus(Duration.ofMinutes(5));
        Work work = accepted(organization, technicianId);
        work.start(start);
        return work;
    }

    /** 5분 전 시작 예정으로 배정해 수락한 작업전 작업. */
    static Work accepted(Organization organization, long technicianId) {
        Instant start = Instant.now().minus(Duration.ofMinutes(5));
        Work work = Work.register(
                new OrganizationId(organization.id()),
                "필터 교체",
                new MembershipId(organization.ownerMembership()),
                start.minus(Duration.ofHours(1)),
                null,
                null,
                null);
        work.assign(
                new WorkSchedule(new TechnicianId(technicianId), start, Duration.ofHours(2)),
                start.minusSeconds(60),
                new MembershipId(organization.ownerMembership()));
        work.accept(start.minusSeconds(30));
        return work;
    }
}

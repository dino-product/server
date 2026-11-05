package com.orbit.schedule.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import com.orbit.schedule.application.port.in.command.SubmitCompletionReportUseCase;
import com.orbit.schedule.application.port.in.command.dto.SubmitCompletionReportCommand;
import com.orbit.support.LocalStackTestSupport;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Tag;
import tools.jackson.databind.JsonNode;

/**
 * 사진을 실제 저장소(LocalStack)에 올려 발급 → 업로드 → 작업 등록·수정·완료보고 → 조회 주소 → 만료 표시까지 확인한다. 저장소 주소를 바꾸므로 다른 API 테스트와
 * 컨텍스트를 함께 쓰지 않는다.
 */
@DisplayName("작업 사진 업로드 흐름(LocalStack)")
class WorkPhotoFlowIntegrationTest extends ScheduleApiTestSupport {

    private static final byte[] JPEG = {
        (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F', 0, 1, 1, 0, 0, 1, 0, 1
    };
    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-', '1', '.', '7', '\n', '%', 0, 0, 0, 0, 0, 0, 0, 0};

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    private SubmitCompletionReportUseCase submitCompletionReportUseCase;

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("app.media.storage.endpoint", LocalStackTestSupport::endpoint);
        registry.add("app.media.storage.bucket", () -> LocalStackTestSupport.BUCKET);
        registry.add("app.media.storage.region", () -> LocalStackTestSupport.REGION);
    }

    @Test
    @DisplayName("올린 현장 사진으로 작업을 등록하면 조회 응답의 주소로 내려받고, 수정으로 뺀 사진은 만료 표시된다")
    void sitePhotosFlow() throws Exception {
        Organization organization = organization();
        long staff = account();
        staffMembership(organization.id(), staff);
        String first = uploadSitePhoto(organization.owner(), organization.id(), JPEG);
        String second = uploadSitePhoto(organization.owner(), organization.id(), JPEG);

        // 다른 직원은 총관리자가 올린 사진을 붙일 수 없다.
        create(staff, organization.id(), List.of(first))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCHEDULE-022"));

        long workId = objectMapper
                .readTree(create(organization.owner(), organization.id(), List.of(first, second))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .path("result")
                .path("workId")
                .asLong();
        assertThat(fileRow(first))
                .containsEntry("status", "ATTACHED")
                .containsEntry("owner_ref", "work:" + workId)
                .containsEntry("object_key", "media/" + organization.id() + "/" + first);

        JsonNode photos =
                detail(organization.owner(), organization.id(), workId).path("sitePhotos");
        assertThat(photos).hasSize(2);
        Iterator<JsonNode> iterator = photos.iterator();
        JsonNode firstPhoto = iterator.next();
        assertThat(firstPhoto.path("photoId").asString()).isEqualTo(first);
        assertThat(download(firstPhoto.path("url").asString())).isEqualTo(JPEG);

        // 이미지가 아닌 파일은 이미지 형식으로 밝혀 올려도 붙지 않는다.
        String fake = uploadSitePhoto(organization.owner(), organization.id(), PDF);
        update(organization.owner(), organization.id(), workId, List.of(first, fake))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCHEDULE-020"));
        assertThat(fileRow(second)).containsEntry("status", "ATTACHED");

        update(organization.owner(), organization.id(), workId, List.of(first)).andExpect(status().isNoContent());
        assertThat(fileRow(first)).containsEntry("status", "ATTACHED");
        assertThat(fileRow(second)).containsEntry("status", "EXPIRED");
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(
                        tags("media/" + organization.id() + "/" + second))
                .containsExactly(Tag.builder().key("status").value("expired").build()));
        assertThat(tags("media/" + organization.id() + "/" + first)).isEmpty();
    }

    @Test
    @DisplayName("대문자로 보낸 사진 식별자도 소문자로 바꿔 붙이고 조회 응답은 소문자 식별자로 내려받는다")
    void acceptsUppercaseIds() throws Exception {
        Organization organization = organization();
        String photo = uploadSitePhoto(organization.owner(), organization.id(), JPEG);

        long workId = objectMapper
                .readTree(create(organization.owner(), organization.id(), List.of(photo.toUpperCase(Locale.ROOT)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .path("result")
                .path("workId")
                .asLong();

        assertThat(fileRow(photo)).containsEntry("status", "ATTACHED").containsEntry("owner_ref", "work:" + workId);
        JsonNode photos =
                detail(organization.owner(), organization.id(), workId).path("sitePhotos");
        assertThat(photos.get(0).path("photoId").asString()).isEqualTo(photo);
        assertThat(download(photos.get(0).path("url").asString())).isEqualTo(JPEG);
    }

    @Test
    @DisplayName("담당 기사가 올린 완료보고 사진으로 보고를 제출하면 관리자 상세에서 내려받는다")
    void completionPhotosFlow() throws Exception {
        Organization organization = organization();
        long technician = account();
        technicianContract(organization.id(), technician);
        long technicianId = jdbcTemplate.queryForObject(
                "select id from technician where company_id = ? and member_id = ?",
                Long.class,
                organization.id(),
                technician);
        long workId = save(PhotoUploadApiIntegrationTest.inProgress(organization, technicianId));
        String before = uploadCompletionPhoto(technician, organization.id(), workId, JPEG);
        String after = uploadCompletionPhoto(technician, organization.id(), workId, JPEG);

        submitCompletionReportUseCase.submit(new SubmitCompletionReportCommand(
                technician, organization.id(), workId, 1, List.of(before), List.of(after), null, null, null, null));

        assertThat(fileRow(before))
                .containsEntry("status", "ATTACHED")
                .containsEntry("purpose", "WORK_COMPLETION_PHOTO")
                .containsEntry("owner_ref", "work:" + workId);
        JsonNode report =
                detail(organization.owner(), organization.id(), workId).path("completionReport");
        assertThat(download(report.path("afterPhotos").get(0).path("url").asString()))
                .isEqualTo(JPEG);
    }

    private String uploadSitePhoto(long account, long organizationId, byte[] bytes) throws Exception {
        return upload(
                post("/api/v1/organizations/{organizationId}/site-photo-uploads", organizationId), account, bytes);
    }

    private String uploadCompletionPhoto(long account, long organizationId, long workId, byte[] bytes)
            throws Exception {
        return upload(
                post(
                        "/api/v1/organizations/{organizationId}/works/{workId}/completion-photo-uploads",
                        organizationId,
                        workId),
                account,
                bytes);
    }

    /** 업로드 주소를 받아 응답의 헤더를 그대로 붙여 올리고 파일 식별자를 돌려준다. */
    private String upload(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder issue,
            long account,
            byte[] bytes)
            throws Exception {
        JsonNode ticket = objectMapper
                .readTree(mockMvc.perform(issue.header(HttpHeaders.AUTHORIZATION, bearer(account))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        Map.of("contentType", "image/jpeg", "sizeBytes", bytes.length))))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .path("result");
        HttpRequest.Builder request = HttpRequest.newBuilder(
                        URI.create(ticket.path("uploadUrl").asString()))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes));
        // Content-Length는 HttpClient가 본문 길이로 붙인다(제한 헤더).
        ticket.path("requiredHeaders").properties().forEach(header -> {
            if (!header.getKey().equalsIgnoreCase("content-length")) {
                request.header(header.getKey(), header.getValue().asString());
            }
        });
        assertThat(http.send(request.build(), HttpResponse.BodyHandlers.discarding())
                        .statusCode())
                .isEqualTo(200);
        return ticket.path("fileId").asString();
    }

    private ResultActions create(long account, long organizationId, List<String> photos) throws Exception {
        return mockMvc.perform(post("/api/v1/organizations/{organizationId}/works", organizationId)
                .header(HttpHeaders.AUTHORIZATION, bearer(account))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "에어컨 설치", "sitePhotoIds", photos))));
    }

    private ResultActions update(long account, long organizationId, long workId, List<String> photos) throws Exception {
        return mockMvc.perform(put("/api/v1/organizations/{organizationId}/works/{workId}", organizationId, workId)
                .header(HttpHeaders.AUTHORIZATION, bearer(account))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "에어컨 설치", "sitePhotoIds", photos))));
    }

    private JsonNode detail(long account, long organizationId, long workId) throws Exception {
        return objectMapper
                .readTree(mockMvc.perform(
                                get("/api/v1/organizations/{organizationId}/works/{workId}", organizationId, workId)
                                        .header(HttpHeaders.AUTHORIZATION, bearer(account)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .path("result");
    }

    private byte[] download(String url) throws Exception {
        HttpResponse<byte[]> response = http.send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200);
        return response.body();
    }

    private Map<String, Object> fileRow(String fileId) {
        return jdbcTemplate.queryForMap(
                "select status, owner_ref, object_key, purpose from uploaded_files where id = ?",
                UUID.fromString(fileId));
    }

    private static List<Tag> tags(String key) {
        try (S3Client s3 = S3Client.builder()
                .endpointOverride(URI.create(LocalStackTestSupport.endpoint()))
                .region(Region.of(LocalStackTestSupport.REGION))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .forcePathStyle(true)
                .build()) {
            return s3.getObjectTagging(request ->
                            request.bucket(LocalStackTestSupport.BUCKET).key(key))
                    .tagSet();
        }
    }
}

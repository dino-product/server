package com.orbit.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.orbit.support.LocalStackTestSupport;
import com.orbit.support.TestcontainersConfiguration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Tag;

/** 실제 PostgreSQL·LocalStack으로 발급 → 업로드 → 확인 → 연결 → 만료 표시를 커밋·롤백 경계와 함께 확인한다. */
@ApplicationModuleTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("media 모듈 조립")
class MediaModuleTest {

    private static final byte[] PNG = {
        (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13, 'I', 'H', 'D', 'R'
    };
    private static final long ORG = 3L;
    private static final long ACCOUNT = 70L;
    private static final String PURPOSE = "WORK_SITE_PHOTO";

    @Autowired
    private MediaFiles mediaFiles;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("app.media.storage.endpoint", LocalStackTestSupport::endpoint);
        registry.add("app.media.storage.bucket", () -> LocalStackTestSupport.BUCKET);
        registry.add("app.media.storage.region", () -> LocalStackTestSupport.REGION);
    }

    @Test
    @DisplayName("올린 사진을 확인·복사한 뒤 트랜잭션 안에서 연결하고, 빠진 사진은 커밋 뒤 만료 표시한다")
    void uploadsAttachesAndExpires() throws Exception {
        String first = upload();
        String second = upload();
        String owner = "work:" + UUID.randomUUID();

        StagedFiles staged = mediaFiles.stage(new StageRequest(ORG, ACCOUNT, PURPOSE, List.of(first, second), null));
        assertThat(staged.findRejection()).isEmpty();
        inTransaction(() -> mediaFiles.attach(staged, owner));

        assertThat(status(first)).isEqualTo("ATTACHED");
        assertThat(objectKey(first)).isEqualTo("media/" + ORG + "/" + first);
        HttpResponse<byte[]> download = http.send(
                HttpRequest.newBuilder(URI.create(mediaFiles.downloadUrl(ORG, first)))
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(download.body()).isEqualTo(PNG);

        // 다른 관리자가 첫 사진만 남기고 고친다.
        StagedFiles kept = mediaFiles.stage(new StageRequest(ORG, 71L, PURPOSE, List.of(first), owner));
        inTransaction(() -> mediaFiles.attach(kept, owner));

        assertThat(status(second)).isEqualTo("EXPIRED");
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(tags("media/" + ORG + "/" + second))
                .containsExactly(Tag.builder().key("status").value("expired").build()));
        assertThat(tags("media/" + ORG + "/" + first)).isEmpty();
    }

    @Test
    @DisplayName("연결 트랜잭션이 롤백되면 파일은 PENDING으로 남아 다시 연결할 수 있고 만료 표시도 남기지 않는다")
    void rollbackLeavesFilesPending() throws Exception {
        String first = upload();
        String second = upload();
        String owner = "work:" + UUID.randomUUID();
        StagedFiles initial = mediaFiles.stage(new StageRequest(ORG, ACCOUNT, PURPOSE, List.of(first), null));
        inTransaction(() -> mediaFiles.attach(initial, owner));

        StagedFiles replacement = mediaFiles.stage(new StageRequest(ORG, ACCOUNT, PURPOSE, List.of(second), owner));
        assertThatThrownBy(() -> inTransaction(() -> {
                    mediaFiles.attach(replacement, owner);
                    throw new IllegalStateException("업무 실패");
                }))
                .hasMessage("업무 실패");

        assertThat(status(first)).isEqualTo("ATTACHED");
        assertThat(status(second)).isEqualTo("PENDING");
        Thread.sleep(500);
        assertThat(tags("media/" + ORG + "/" + first)).isEmpty();

        StagedFiles retried = mediaFiles.stage(new StageRequest(ORG, ACCOUNT, PURPOSE, List.of(second), owner));
        inTransaction(() -> mediaFiles.attach(retried, owner));
        assertThat(status(second)).isEqualTo("ATTACHED");
        assertThat(status(first)).isEqualTo("EXPIRED");
    }

    @Test
    @DisplayName("확인은 트랜잭션 밖에서만, 연결은 트랜잭션 안에서만 할 수 있다")
    void enforcesTransactionBoundaries() throws Exception {
        String fileId = upload();
        StageRequest request = new StageRequest(ORG, ACCOUNT, PURPOSE, List.of(fileId), null);

        assertThatThrownBy(() -> inTransaction(() -> mediaFiles.stage(request)))
                .isInstanceOf(IllegalStateException.class);
        StagedFiles staged = mediaFiles.stage(request);
        assertThatThrownBy(() -> mediaFiles.attach(staged, "work:1"))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }

    private String upload() throws Exception {
        UploadTicket ticket = new TransactionTemplate(transactionManager)
                .execute(status -> mediaFiles.issueUpload(
                        new UploadRequest(ORG, ACCOUNT, new UploadSpec(PURPOSE, 1024), "image/png", PNG.length)));
        HttpRequest.Builder put =
                HttpRequest.newBuilder(URI.create(ticket.uploadUrl())).PUT(HttpRequest.BodyPublishers.ofByteArray(PNG));
        for (Map.Entry<String, String> header : ticket.requiredHeaders().entrySet()) {
            if (!header.getKey().equalsIgnoreCase("content-length")) {
                put.header(header.getKey(), header.getValue());
            }
        }
        assertThat(http.send(put.build(), HttpResponse.BodyHandlers.discarding())
                        .statusCode())
                .isEqualTo(200);
        return ticket.fileId();
    }

    private void inTransaction(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
    }

    private String status(String fileId) {
        return jdbc.queryForObject(
                "select status from uploaded_files where id = ?", String.class, UUID.fromString(fileId));
    }

    private String objectKey(String fileId) {
        return jdbc.queryForObject(
                "select object_key from uploaded_files where id = ?", String.class, UUID.fromString(fileId));
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

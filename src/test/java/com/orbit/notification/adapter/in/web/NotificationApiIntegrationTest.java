package com.orbit.notification.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.orbit.support.IntegrationTestSupport;

/**
 * 요청자는 실제 Access Token 인증을 거친 auth의 {@code AccountPrincipal}에서 얻는다. 알림은 schedule 이벤트로 만들어지므로 여기서는 행을 SQL로 넣고 알림센터
 * API만 본다. 계정 식별자를 테스트마다 달리해 같은 DB의 다른 테스트 행과 섞이지 않게 한다.
 */
@DisplayName("알림센터·알림 설정 API")
class NotificationApiIntegrationTest extends IntegrationTestSupport {

    private static final AtomicLong ACCOUNT_IDS = new AtomicLong(30_000);
    private static final Instant NOTIFIED_AT = Instant.parse("2026-10-19T01:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${app.auth.jwt.secret}")
    private String jwtSecret;

    @Value("${app.auth.jwt.issuer}")
    private String jwtIssuer;

    @Test
    @DisplayName("본인 알림만 최신순으로 쪽을 나눠 돌려주고 숨긴 알림은 빼며 안 읽은 알림만 고를 수 있다")
    void listsOwnVisibleNotificationsLatestFirst() throws Exception {
        long accountId = ACCOUNT_IDS.incrementAndGet();
        long otherAccountId = ACCOUNT_IDS.incrementAndGet();
        long oldest = insert(accountId, "WORK_ASSIGNED", 10L, NOTIFIED_AT, false, false);
        long read = insert(accountId, "WORK_CANCELED", 11L, NOTIFIED_AT.plusSeconds(60), true, false);
        insert(accountId, "WORK_RESCHEDULED", 12L, NOTIFIED_AT.plusSeconds(120), false, true);
        long newest = insert(accountId, "WORK_ACCEPTED", 13L, NOTIFIED_AT.plusSeconds(180), false, false);
        insert(otherAccountId, "WORK_ASSIGNED", 10L, NOTIFIED_AT.plusSeconds(240), false, false);

        mockMvc.perform(authorized(get("/api/v1/notifications").param("size", "2"), accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result.totalCount").value(3))
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(2))
                .andExpect(jsonPath("$.result.notifications.length()").value(2))
                .andExpect(jsonPath("$.result.notifications[0].id").value(newest))
                .andExpect(jsonPath("$.result.notifications[0].type").value("WORK_ACCEPTED"))
                .andExpect(jsonPath("$.result.notifications[0].organizationId").value(1))
                .andExpect(jsonPath("$.result.notifications[0].workId").value(13))
                .andExpect(jsonPath("$.result.notifications[0].message").value("문구 13"))
                .andExpect(jsonPath("$.result.notifications[0].read").value(false))
                .andExpect(jsonPath("$.result.notifications[0].createdAt")
                        .value(NOTIFIED_AT.plusSeconds(180).toString()))
                .andExpect(jsonPath("$.result.notifications[1].id").value(read))
                .andExpect(jsonPath("$.result.notifications[1].read").value(true));

        mockMvc.perform(authorized(get("/api/v1/notifications").param("unreadOnly", "true"), accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalCount").value(2))
                .andExpect(jsonPath("$.result.notifications[0].id").value(newest))
                .andExpect(jsonPath("$.result.notifications[1].id").value(oldest));
    }

    @Test
    @DisplayName("쪽 입력이 범위를 벗어나면 NOTIFICATION-001, 형식이 틀리면 COMMON-400이다")
    void rejectsInvalidPage() throws Exception {
        long accountId = ACCOUNT_IDS.incrementAndGet();

        mockMvc.perform(authorized(get("/api/v1/notifications").param("size", "101"), accountId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOTIFICATION-001"));
        mockMvc.perform(authorized(get("/api/v1/notifications").param("page", "abc"), accountId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
    }

    @Test
    @DisplayName("본인 알림을 읽으면 안 읽은 수가 줄고 다시 읽어도 성공한다")
    void readsOwnNotificationIdempotently() throws Exception {
        long accountId = ACCOUNT_IDS.incrementAndGet();
        long first = insert(accountId, "WORK_ASSIGNED", 10L, NOTIFIED_AT, false, false);
        insert(accountId, "WORK_CANCELED", 11L, NOTIFIED_AT.plusSeconds(60), false, false);
        insert(accountId, "WORK_RESCHEDULED", 12L, NOTIFIED_AT.plusSeconds(120), false, true);

        mockMvc.perform(authorized(get("/api/v1/notifications/unread-count"), accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.count").value(2));

        mockMvc.perform(authorized(put("/api/v1/notifications/{id}/read", first), accountId))
                .andExpect(status().isNoContent());
        mockMvc.perform(authorized(put("/api/v1/notifications/{id}/read", first), accountId))
                .andExpect(status().isNoContent());

        mockMvc.perform(authorized(get("/api/v1/notifications/unread-count"), accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.count").value(1));
    }

    @Test
    @DisplayName("다른 계정의 알림 읽음은 403, 없거나 숨긴 알림은 404로 거부한다")
    void rejectsReadingOthersOrMissingNotification() throws Exception {
        long accountId = ACCOUNT_IDS.incrementAndGet();
        long others = insert(ACCOUNT_IDS.incrementAndGet(), "WORK_ASSIGNED", 10L, NOTIFIED_AT, false, false);
        long hidden = insert(accountId, "WORK_ASSIGNED", 10L, NOTIFIED_AT, false, true);

        mockMvc.perform(authorized(put("/api/v1/notifications/{id}/read", others), accountId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOTIFICATION-003"));
        mockMvc.perform(authorized(put("/api/v1/notifications/{id}/read", hidden), accountId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION-002"));
        mockMvc.perform(authorized(put("/api/v1/notifications/{id}/read", Long.MAX_VALUE), accountId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION-002"));
    }

    @Test
    @DisplayName("알림 설정은 처음에 모두 켜짐이고 저장하면 본인 설정만 바뀐다")
    void readsAndChangesOwnSetting() throws Exception {
        long accountId = ACCOUNT_IDS.incrementAndGet();
        long otherAccountId = ACCOUNT_IDS.incrementAndGet();

        mockMvc.perform(authorized(get("/api/v1/notification-settings"), accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.workAssignmentEnabled").value(true))
                .andExpect(jsonPath("$.result.scheduleChangeEnabled").value(true))
                .andExpect(jsonPath("$.result.workCancellationEnabled").value(true));

        mockMvc.perform(authorized(put("/api/v1/notification-settings"), accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workAssignmentEnabled": false, "scheduleChangeEnabled": true,
                                 "workCancellationEnabled": false}
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(authorized(get("/api/v1/notification-settings"), accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.workAssignmentEnabled").value(false))
                .andExpect(jsonPath("$.result.scheduleChangeEnabled").value(true))
                .andExpect(jsonPath("$.result.workCancellationEnabled").value(false));
        mockMvc.perform(authorized(get("/api/v1/notification-settings"), otherAccountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.workAssignmentEnabled").value(true));
    }

    @Test
    @DisplayName("알림 설정 값이 빠지면 COMMON-400으로 거부한다")
    void rejectsIncompleteSetting() throws Exception {
        mockMvc.perform(authorized(put("/api/v1/notification-settings"), ACCOUNT_IDS.incrementAndGet())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workAssignmentEnabled\": false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
    }

    @Test
    @DisplayName("인증하지 않은 요청은 401로 거부한다")
    void rejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON-401"));
        mockMvc.perform(get("/api/v1/notification-settings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON-401"));
    }

    private long insert(long accountId, String type, Long workId, Instant notifiedAt, boolean read, boolean hidden) {
        return jdbcTemplate.queryForObject(
                """
                insert into notifications
                    (member_id, company_id, type, work_id, message, notified_at, status, read_at, is_hidden,
                     created_at, updated_at)
                values (?, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                returning id
                """,
                Long.class,
                accountId,
                type,
                workId,
                "문구 " + workId,
                Timestamp.from(notifiedAt),
                read ? "READ" : "UNREAD",
                read ? Timestamp.from(notifiedAt.plusSeconds(1)) : null,
                hidden,
                Timestamp.from(notifiedAt),
                Timestamp.from(notifiedAt));
    }

    private MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request, long accountId)
            throws Exception {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(accountId));
    }

    private String accessToken(long accountId) throws Exception {
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
        return jwt.serialize();
    }
}

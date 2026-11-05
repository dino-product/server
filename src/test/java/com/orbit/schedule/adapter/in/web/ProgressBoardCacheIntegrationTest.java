package com.orbit.schedule.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import com.orbit.realtime.application.port.in.command.PublishRealtimeSignalUseCase;
import com.orbit.schedule.application.port.out.TechnicianProfile;
import com.orbit.schedule.application.port.out.TypeLabel;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.TechnicianId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkSchedule;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * 진행 보드 캐시를 켠 실제 조립으로 저장·적중·무효화 순서를 확인한다. 캐시 Redis 주소가 없어 테스트 Redis(기본 Redis)를 쓰며, 테스트 클래스마다 비워진다.
 * realtime 신호 발행은 대체해 발행 시점의 캐시 세대를 기록한다.
 */
@DisplayName("진행 보드 캐시")
@TestPropertySource(properties = "app.schedule.board-cache.enabled=true")
class ProgressBoardCacheIntegrationTest extends ScheduleApiTestSupport {

    private static final Instant DAY = Instant.parse("2030-03-02T00:00:00Z");
    private static final TechnicianId TECHNICIAN = new TechnicianId(41L);

    @MockitoBean
    PublishRealtimeSignalUseCase publishRealtimeSignal;

    @Autowired
    MeterRegistry meters;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Autowired
    ApplicationContext context;

    @Test
    @DisplayName("다시 조회하면 저장해 둔 보드를 쓰고, 작업을 바꾸면 실시간 신호보다 먼저 버려 다음 조회가 바뀐 보드를 받는다")
    void servesStoredBoardAndInvalidatesBeforeSignal() throws Exception {
        Organization organization = organization();
        OrganizationId organizationId = new OrganizationId(organization.id());
        given(technicianProfiles.findProfiles(eq(organizationId), any()))
                .willReturn(Map.of(
                        TECHNICIAN,
                        new TechnicianProfile(TECHNICIAN, "김민수", new TypeLabel(7L, "일반 기사", "#3366FF", false))));
        Work work = Work.register(
                organizationId,
                "에어컨 설치",
                new MembershipId(organization.ownerMembership()),
                Instant.now().minus(Duration.ofHours(1)),
                null,
                null,
                null);
        work.assign(
                new WorkSchedule(TECHNICIAN, DAY, Duration.ofHours(2)),
                Instant.now().minusSeconds(60),
                new MembershipId(organization.ownerMembership()));
        long workId = save(work);

        String built = board(organization)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.columns[1].cards[0].workId").value(workId))
                .andExpect(jsonPath("$.result.columns[1].cards[0].technician.technicianType.name")
                        .value("일반 기사"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        double hits = count("hit");
        String stored = board(organization)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(stored).as("저장한 보드의 응답은 만든 보드와 같다").isEqualTo(built);
        assertThat(count("hit")).as("두 번째 조회는 저장한 보드").isEqualTo(hits + 1);

        long generationBefore = generation(organization.id());
        AtomicLong generationAtSignal = new AtomicLong(-1);
        doAnswer(invocation -> {
                    generationAtSignal.set(generation(organization.id()));
                    return null;
                })
                .when(publishRealtimeSignal)
                .publish(any());
        mockMvc.perform(post(
                                "/api/v1/organizations/{organizationId}/works/{workId}/cancellation",
                                organization.id(),
                                workId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(organization.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"CUSTOMER_REQUEST\"}"))
                .andExpect(status().isNoContent());

        verify(publishRealtimeSignal, timeout(5_000).atLeastOnce()).publish(any());
        assertThat(generationAtSignal.get()).as("신호를 보낼 때 이미 버렸다").isNotEqualTo(generationBefore);
        board(organization)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.columns[1].cards.length()").value(0))
                .andExpect(jsonPath("$.result.summary.total").value(0));
    }

    @Test
    @DisplayName("캐시 Redis 연결은 Bean으로 등록하지 않아 기본 Redis 연결과 템플릿이 하나씩 그대로 있다")
    void keepsDefaultRedisBeans() {
        assertThat(context.getBeansOfType(RedisConnectionFactory.class)).hasSize(1);
        assertThat(context.getBeansOfType(StringRedisTemplate.class)).hasSize(1);
    }

    private ResultActions board(Organization organization) throws Exception {
        return mockMvc.perform(get(
                        "/api/v1/organizations/{organizationId}/progress-board?from={from}&to={to}",
                        organization.id(),
                        DAY,
                        DAY.plus(Duration.ofDays(1)))
                .header(HttpHeaders.AUTHORIZATION, bearer(organization.owner())));
    }

    private long generation(long organizationId) {
        String value = redisTemplate.opsForValue().get("orbit:schedule:board-gen:" + organizationId);
        return value == null ? 0 : Long.parseLong(value);
    }

    private double count(String result) {
        Counter counter =
                meters.find("schedule.board.cache").tag("result", result).counter();
        return counter == null ? 0 : counter.count();
    }
}

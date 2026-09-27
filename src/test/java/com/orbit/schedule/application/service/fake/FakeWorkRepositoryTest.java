package com.orbit.schedule.application.service.fake;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.schedule.domain.CompletionReport;
import com.orbit.schedule.domain.CustomerInfo;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.Money;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.PaymentInfo;
import com.orbit.schedule.domain.PaymentMethod;
import com.orbit.schedule.domain.RejectionReason;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkTypeId;

/**
 * 서비스 테스트가 기대는 fake 저장소가 작업의 모든 값을 잃지 않고 복사하는지 확인한다. 필드를 재귀로 비교하므로 Work에 값이 늘었는데 복사에서 빠지면 여기서
 * 드러난다.
 */
@DisplayName("서비스 테스트용 작업 저장소")
class FakeWorkRepositoryTest {

    private static final MembershipId MANAGER_ID = new MembershipId(99L);

    private static final OrganizationId ORGANIZATION_ID = new OrganizationId(100L);
    private static final Instant NOW = Instant.parse("2026-09-24T01:00:00Z");

    @Test
    @DisplayName("저장한 작업을 조회하면 식별자 말고는 모든 값이 같은 사본을 돌려준다")
    void roundTripsEveryValue() {
        FakeWorkRepository repository = new FakeWorkRepository();
        Work work = completedWorkWithRejectedHistory();

        WorkId id = repository.store(work);
        Work found = repository.findInOrganization(ORGANIZATION_ID, id).orElseThrow();

        assertThat(found).isNotSameAs(work);
        assertThat(found.id()).contains(id);
        assertThat(found).usingRecursiveComparison().ignoringFields("id").isEqualTo(work);
    }

    private static Work completedWorkWithRejectedHistory() {
        Work work = Work.register(
                ORGANIZATION_ID,
                "에어컨 수리",
                new MembershipId(1L),
                new WorkTypeId(2L),
                new CustomerInfo("홍길동", "010-1234-5678", "서울시"),
                new PaymentInfo(new Money(150_000L), PaymentMethod.ON_SITE_CARD));
        work.assign(new WorkSchedule(new MembershipId(3L), NOW, Duration.ofHours(2)), NOW, MANAGER_ID);
        work.reject(RejectionReason.SCHEDULE_CONFLICT, NOW.plusSeconds(10));
        work.assign(new WorkSchedule(new MembershipId(4L), NOW, Duration.ofHours(2)), NOW.plusSeconds(20), MANAGER_ID);
        work.accept(NOW.plusSeconds(30));
        work.reassign(
                new WorkSchedule(new MembershipId(5L), NOW, Duration.ofHours(2)), NOW.plusSeconds(40), MANAGER_ID);
        work.accept(NOW.plusSeconds(50));
        work.start();
        work.submitCompletionReport(new CompletionReport(null, null, null, null, null, null));
        return work;
    }
}

package com.orbit.schedule.adapter.out.memory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.domain.AssignmentHistory;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;

/**
 * 임시 WorkRepository 구현. JPA 영속성 어댑터(HM-234)로 교체한 뒤 삭제한다. {@code local}·{@code test} 프로필에서만 등록하고 {@code prod}와 그 밖의
 * 환경에서는 등록하지 않으므로, 이 포트를 쓰는 서비스가 있는 지금 그 환경들은 기동에 실패한다(의도).
 *
 * <p>실제 저장소처럼 저장·조회 때 복사본을 주고받아 save 없이 바꾼 내용이 저장값에 섞이지 않게 하고, 식별자가 있는데 저장소에 없는 작업은 거부한다. JPA와 다른
 * 점: 재시작하면 데이터가 사라지고, 트랜잭션에 참여하지 않아 롤백되지 않으며, 버전 기반 동시 변경 검출이 없다. 저장 시 재구성 검증 예외는 도메인 규칙 변환 밖에서
 * 나므로 그대로 전파된다. 따라서 롤백·동시 변경 동작은 이 구현으로 검증했다고 보지 않는다.
 */
@Component
@Profile({"local & !prod", "test & !prod"})
class InMemoryWorkRepository implements WorkRepository {

    private final Map<Long, Work> store = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    @Override
    public Work save(Work work) {
        if (work.id().isPresent()) {
            WorkId id = work.id().get();
            Work stored = copyOf(work, id);
            if (store.replace(id.value(), stored) == null) {
                throw new IllegalStateException("Cannot save unknown work: " + id.value());
            }
            return copyOf(stored, id);
        }
        WorkId id = new WorkId(sequence.incrementAndGet());
        Work stored = copyOf(work, id);
        store.put(id.value(), stored);
        return copyOf(stored, id);
    }

    @Override
    public Optional<Work> findInOrganization(OrganizationId organizationId, WorkId workId) {
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(workId, "workId must not be null");
        return Optional.ofNullable(store.get(workId.value()))
                .filter(work -> work.organizationId().equals(organizationId))
                .map(work -> copyOf(work, workId));
    }

    @Override
    public List<Work> listActiveByTechnician(OrganizationId organizationId, MembershipId technicianId) {
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(technicianId, "technicianId must not be null");
        return store.values().stream()
                .filter(work -> work.organizationId().equals(organizationId))
                .filter(work -> work.status().isActive())
                .filter(work -> work.schedule()
                        .map(schedule -> schedule.technicianId().equals(technicianId))
                        .orElse(false))
                .map(work -> copyOf(work, work.id().orElseThrow()))
                .toList();
    }

    private static Work copyOf(Work work, WorkId id) {
        List<AssignmentHistory> histories = work.assignmentHistory().stream()
                .map(history -> AssignmentHistory.restore(
                        history.schedule(),
                        history.assignedAt(),
                        history.assignedBy(),
                        history.result(),
                        history.rejection().orElse(null),
                        history.decidedAt().orElse(null),
                        history.ending().orElse(null)))
                .toList();
        return Work.reconstitute(
                id,
                work.organizationId(),
                work.name(),
                work.registrarId(),
                work.workType().orElse(null),
                work.schedule().orElse(null),
                work.customerInfo(),
                work.paymentInfo(),
                work.status(),
                histories,
                work.completionReport().orElse(null));
    }
}

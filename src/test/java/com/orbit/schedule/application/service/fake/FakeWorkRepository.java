package com.orbit.schedule.application.service.fake;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.domain.AssignmentHistory;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;

/**
 * 서비스 단위 테스트용 WorkRepository. 스캔되지 않도록 애너테이션을 붙이지 않는다. 실제 저장소처럼 저장·조회 때 복사본을 주고받아 save 없는 변경이나 save 뒤 변경이
 * 저장값에 섞이지 않게 하고, 식별자가 있는데 없는 작업의 저장은 거부한다. 저장 호출마다 그 시점의 복사본을 기록한다.
 */
public class FakeWorkRepository implements WorkRepository {

    private final Map<Long, Work> store = new HashMap<>();
    private final List<Work> saved = new ArrayList<>();
    private long sequence;

    @Override
    public Work save(Work work) {
        WorkId id = work.id().orElseGet(() -> new WorkId(++sequence));
        if (work.id().isPresent() && !store.containsKey(id.value())) {
            throw new IllegalStateException("Cannot save unknown work: " + id.value());
        }
        store.put(id.value(), copyOf(work, id));
        saved.add(copyOf(work, id));
        return copyOf(work, id);
    }

    @Override
    public Optional<Work> findById(WorkId workId) {
        return Optional.ofNullable(store.get(workId.value())).map(work -> copyOf(work, workId));
    }

    @Override
    public List<Work> findActiveByTechnician(OrganizationId organizationId, MembershipId technicianId) {
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

    /** 저장 호출마다 그 시점의 작업. */
    public List<Work> saved() {
        return List.copyOf(saved);
    }

    /** 테스트 준비로 저장한 기록을 지워, 이후 서비스가 저장했는지만 보이게 한다. */
    public void clearSaveHistory() {
        saved.clear();
    }

    private static Work copyOf(Work work, WorkId id) {
        List<AssignmentHistory> histories = work.assignmentHistory().stream()
                .map(history -> AssignmentHistory.restore(
                        history.schedule(),
                        history.assignedAt(),
                        history.result(),
                        history.rejectionReason().orElse(null),
                        history.decidedAt().orElse(null)))
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

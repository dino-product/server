package com.orbit.schedule.application.service.fake;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import com.orbit.schedule.application.port.out.WorkQueryPort;
import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.application.port.out.WorkSearchCriteria;
import com.orbit.schedule.application.port.out.WorkSearchResult;
import com.orbit.schedule.domain.AssignmentHistory;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.TechnicianId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkStatus;

/**
 * 서비스 단위 테스트용 WorkRepository. 스캔되지 않도록 애너테이션을 붙이지 않는다. 실제 저장소처럼 저장·조회 때 복사본을 주고받아 save 없는 변경이나 save 뒤 변경이
 * 저장값에 섞이지 않게 하고, 식별자가 있는데 없는 작업의 저장은 거부한다. 서비스가 저장할 때마다 그 시점의 복사본을 기록한다. 기사 활성 작업 목록은 포트 계약대로
 * 순서를 보장하지 않으므로, 서비스가 순서를 정하는지 드러나도록 저장한 순서의 역순으로 돌려준다. 미리 입력한 담당기사로 찾는 대기함 작업과 조회용
 * 목록({@link WorkQueryPort})도 같은 저장소에서 같은 역순으로 돌려준다.
 */
public class FakeWorkRepository implements WorkRepository, WorkQueryPort {

    private final Map<Long, Work> store = new LinkedHashMap<>();
    private final List<Work> saved = new ArrayList<>();
    private long sequence;
    private int activeByTechnicianQueryCount;
    private final List<WorkSearchCriteria> searches = new ArrayList<>();
    private WorkSearchResult searchResult;
    private final List<WorkSearchCriteria> counts = new ArrayList<>();
    private final Map<Predicate<WorkSearchCriteria>, Long> givenCounts = new LinkedHashMap<>();

    @Override
    public Work save(Work work) {
        Work stored = put(work);
        saved.add(copyOf(stored, stored.id().orElseThrow()));
        return stored;
    }

    /** 테스트 준비용 저장. 서비스의 저장 기록({@link #saved()})에는 남기지 않는다. */
    public WorkId store(Work work) {
        return put(work).id().orElseThrow();
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
    public List<Work> listActiveByTechnician(OrganizationId organizationId, TechnicianId technicianId) {
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(technicianId, "technicianId must not be null");
        activeByTechnicianQueryCount++;
        return store.values().stream()
                .filter(work -> work.organizationId().equals(organizationId))
                .filter(work -> work.status().isActive())
                .filter(work -> work.schedule()
                        .map(schedule -> schedule.technicianId().equals(technicianId))
                        .orElse(false))
                .map(work -> copyOf(work, work.id().orElseThrow()))
                .toList()
                .reversed();
    }

    @Override
    public List<Work> listBacklogPlannedForTechnician(OrganizationId organizationId, TechnicianId technicianId) {
        Objects.requireNonNull(technicianId, "technicianId must not be null");
        return listBacklog(organizationId).stream()
                .filter(work -> work.plannedSchedule()
                        .map(planned -> technicianId.equals(planned.technicianId()))
                        .orElse(false))
                .toList();
    }

    @Override
    public List<Work> listScheduledBetween(OrganizationId organizationId, Instant from, Instant to) {
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        return inOrganization(organizationId).stream()
                .filter(work -> work.status() != WorkStatus.CANCELLED)
                .filter(work -> work.schedule()
                        .map(schedule -> schedule.startTime().isBefore(to)
                                && schedule.endTime().isAfter(from))
                        .orElse(false))
                .toList();
    }

    @Override
    public List<Work> listScheduledForTechnician(
            OrganizationId organizationId, TechnicianId technicianId, Instant from, Instant to) {
        Objects.requireNonNull(technicianId, "technicianId must not be null");
        return listScheduledBetween(organizationId, from, to).stream()
                .filter(work -> work.schedule().orElseThrow().technicianId().equals(technicianId))
                .toList();
    }

    @Override
    public List<Work> listAssignedToTechnician(
            OrganizationId organizationId, TechnicianId technicianId, Instant from, Instant to) {
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(technicianId, "technicianId must not be null");
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        return inOrganization(organizationId).stream()
                .filter(work -> work.assignmentHistory().stream()
                        .map(AssignmentHistory::schedule)
                        .anyMatch(schedule -> schedule.technicianId().equals(technicianId)
                                && schedule.startTime().isBefore(to)
                                && schedule.endTime().isAfter(from)))
                .toList();
    }

    @Override
    public List<Work> listBacklog(OrganizationId organizationId) {
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        return inOrganization(organizationId).stream()
                .filter(work -> work.status() == WorkStatus.REGISTERED)
                .toList();
    }

    /**
     * 검색 조건은 해석하지 않고 기록만 한다(조건 해석은 실제 어댑터의 몫). {@link #givenSearchResult}로 정한 결과가 있으면 그것을, 없으면 조직의 모든 작업을 저장
     * 역순으로 돌려준다. 저장 시각을 따로 두지 않으므로 정하지 않은 최근 수정 시각은 등록 시각으로 채운다.
     */
    @Override
    public WorkSearchResult search(OrganizationId organizationId, WorkSearchCriteria criteria) {
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(criteria, "criteria must not be null");
        searches.add(criteria);
        if (searchResult != null) {
            return searchResult;
        }
        List<Work> works = inOrganization(organizationId);
        return new WorkSearchResult(
                works.stream()
                        .map(work -> new WorkSearchResult.SearchedWork(work, work.registeredAt()))
                        .toList(),
                works.size());
    }

    /** 검색이 돌려줄 작업(이 순서 그대로)과 각 작업의 최근 수정 시각, 전체 수를 정한다. */
    public void givenSearchResult(List<WorkId> workIds, List<Instant> lastModifiedAts, long totalCount) {
        if (workIds.size() != lastModifiedAts.size()) {
            throw new IllegalArgumentException("each work needs its last modified time");
        }
        List<WorkSearchResult.SearchedWork> works = new ArrayList<>();
        for (int i = 0; i < workIds.size(); i++) {
            WorkId id = workIds.get(i);
            works.add(new WorkSearchResult.SearchedWork(copyOf(store.get(id.value()), id), lastModifiedAts.get(i)));
        }
        searchResult = new WorkSearchResult(works, totalCount);
    }

    /**
     * 조건 해석 없이 기록만 하고, {@link #givenCount}로 정한 수 가운데 조건에 처음 맞는 것을, 없으면 조직의 모든 작업 수를 돌려준다.
     */
    @Override
    public long count(OrganizationId organizationId, WorkSearchCriteria criteria) {
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(criteria, "criteria must not be null");
        counts.add(criteria);
        return givenCounts.entrySet().stream()
                .filter(entry -> entry.getKey().test(criteria))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseGet(() -> (long) inOrganization(organizationId).size());
    }

    /** 이 조건으로 셀 때 돌려줄 수를 정한다. */
    public void givenCount(Predicate<WorkSearchCriteria> matches, long count) {
        givenCounts.put(matches, count);
    }

    /** 서비스가 센 조건. */
    public List<WorkSearchCriteria> counts() {
        return List.copyOf(counts);
    }

    /** 서비스가 검색한 조건. */
    public List<WorkSearchCriteria> searches() {
        return List.copyOf(searches);
    }

    /** 서비스가 저장할 때마다 그 시점의 작업. */
    public List<Work> saved() {
        return List.copyOf(saved);
    }

    /** 기사의 활성 작업을 조회한 횟수. */
    public int activeByTechnicianQueryCount() {
        return activeByTechnicianQueryCount;
    }

    private List<Work> inOrganization(OrganizationId organizationId) {
        return store.values().stream()
                .filter(work -> work.organizationId().equals(organizationId))
                .map(work -> copyOf(work, work.id().orElseThrow()))
                .toList()
                .reversed();
    }

    private Work put(Work work) {
        WorkId id = work.id().orElseGet(() -> new WorkId(++sequence));
        if (work.id().isPresent() && !store.containsKey(id.value())) {
            throw new IllegalStateException("Cannot save unknown work: " + id.value());
        }
        store.put(id.value(), copyOf(work, id));
        return copyOf(work, id);
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
                work.registeredAt(),
                work.workType().orElse(null),
                work.schedule().orElse(null),
                work.plannedSchedule().orElse(null),
                work.customerInfo(),
                work.paymentInfo(),
                work.status(),
                histories,
                work.completionReport().orElse(null),
                work.startedAt().orElse(null),
                work.completedAt().orElse(null),
                work.cancellation().orElse(null),
                work.statusCorrections());
    }
}

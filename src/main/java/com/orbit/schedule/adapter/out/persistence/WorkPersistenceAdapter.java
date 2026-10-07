package com.orbit.schedule.adapter.out.persistence;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.TechnicianId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkStatus;

/**
 * {@link WorkRepository}의 JPA 구현. 조회 결과는 Entity에서 새로 만든 Domain 객체라 영속 상태와 분리된 사본이며, {@link #save}하지 않은 변경은
 * 커밋돼도 저장되지 않는다.
 *
 * <p>기존 작업의 저장은 영속성 컨텍스트의 managed Entity를 찾아 제자리에서 갱신한다(새 Entity로 {@code merge}하지 않음). 그래야 {@code @Version}이
 * 트랜잭션 안에서 읽은 버전으로 동시 변경을 검출하고 자식 행이 삭제·재삽입되지 않는다. 메서드마다 트랜잭션을 두어 서비스 트랜잭션이 있으면 참여하고, 없으면
 * (테스트 준비 등) 자체 트랜잭션에서 자식 컬렉션 지연 로딩과 갱신 flush를 마친다. 서비스 트랜잭션 밖에서 읽은 뒤 다시 저장하면 마지막 요청이 이기므로,
 * 화면이 본 버전과의 비교는 명령에 버전을 담는 HM-236에서 정한다. 버전 충돌을 어떤 오류 코드로 알릴지도 HM-236 범위이고 지금은 변환하지 않는다.
 *
 * <p>상태 정정 기록({@code StatusCorrection})은 ERD에 자리가 없어 저장하지 못한다. 정정 기록이 있는 작업은 복원 검증이 깨지므로 저장을 거부한다(S-11).
 */
@Repository
class WorkPersistenceAdapter implements WorkRepository {

    private static final Set<WorkStatus> ACTIVE_STATUSES =
            EnumSet.of(WorkStatus.PENDING_ACCEPTANCE, WorkStatus.ACCEPTED, WorkStatus.IN_PROGRESS);

    private final SpringDataWorkRepository repository;

    WorkPersistenceAdapter(SpringDataWorkRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    @Transactional
    public Work save(Work work) {
        Objects.requireNonNull(work, "work must not be null");
        if (!work.statusCorrections().isEmpty()) {
            throw new IllegalStateException(
                    "status corrections cannot be persisted: the ERD has no table for them (S-11)");
        }
        if (work.id().isEmpty()) {
            return WorkMapper.toDomain(repository.save(WorkMapper.toEntity(work)));
        }
        Long id = work.id().get().value();
        WorkJpaEntity entity = repository
                .findByIdAndCompanyId(id, work.organizationId().value())
                .orElseThrow(() -> new IllegalStateException("Cannot save unknown work: " + id));
        WorkMapper.applyChanges(entity, work);
        return WorkMapper.toDomain(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Work> findInOrganization(OrganizationId organizationId, WorkId workId) {
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(workId, "workId must not be null");
        return repository
                .findByIdAndCompanyId(workId.value(), organizationId.value())
                .map(WorkMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Work> listActiveByTechnician(OrganizationId organizationId, TechnicianId technicianId) {
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(technicianId, "technicianId must not be null");
        return repository
                .findByCompanyIdAndTechnicianIdAndStatusIn(
                        organizationId.value(), technicianId.value(), ACTIVE_STATUSES)
                .stream()
                .map(WorkMapper::toDomain)
                .toList();
    }
}

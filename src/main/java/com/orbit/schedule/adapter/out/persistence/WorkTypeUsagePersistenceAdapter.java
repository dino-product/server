package com.orbit.schedule.adapter.out.persistence;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.persistence.EntityManager;

import org.hibernate.query.NativeQuery;
import org.hibernate.type.StandardBasicTypes;
import org.springframework.stereotype.Repository;

import com.orbit.schedule.application.port.out.ClearedWorkType;
import com.orbit.schedule.application.port.out.WorkTypeUsagePort;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.TechnicianId;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkStatus;
import com.orbit.schedule.domain.WorkTypeId;

/**
 * 작업 유형 사용 현황과 해제. 해제는 작업 행만 바꾸는 한 문장의 UPDATE라 영속성 컨텍스트를 거치지 않으므로, 같은 트랜잭션에서 작업 Entity를 먼저 읽어 두지 않는
 * 호출(organization의 유형 삭제)에서만 쓴다. UPDATE는 조건에 맞는 행을 잠그며, 다른 트랜잭션이 바꾸는 행은 그 커밋 뒤의 값으로 조건을 다시 본다.
 */
@Repository
class WorkTypeUsagePersistenceAdapter implements WorkTypeUsagePort {

    private static final String COUNT_BY_STATUS = """
            select w.status, count(*) as works
            from work w
            where w.company_id = :organizationId and w.job_type_id = :workTypeId
            group by w.status
            """;

    private static final String CLEAR_WORK_TYPE = """
            update work
            set job_type_id = null, version = version + 1, updated_at = :at
            where company_id = :organizationId and job_type_id = :workTypeId and status in (:statuses)
            returning id, status, technician_id
            """;

    private final EntityManager entityManager;

    WorkTypeUsagePersistenceAdapter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<WorkStatus, Long> countByStatus(OrganizationId organizationId, WorkTypeId workTypeId) {
        List<Object[]> rows = entityManager
                .createNativeQuery(COUNT_BY_STATUS)
                .unwrap(NativeQuery.class)
                .addScalar("status", StandardBasicTypes.STRING)
                .addScalar("works", StandardBasicTypes.LONG)
                .setParameter("organizationId", organizationId.value())
                .setParameter("workTypeId", workTypeId.value())
                .getResultList();
        Map<WorkStatus, Long> counts = new EnumMap<>(WorkStatus.class);
        rows.forEach(row -> counts.put(WorkStatus.valueOf((String) row[0]), (Long) row[1]));
        return counts;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ClearedWorkType> clearWorkType(
            OrganizationId organizationId, WorkTypeId workTypeId, Set<WorkStatus> statuses, Instant at) {
        if (statuses.isEmpty()) {
            return List.of();
        }
        List<Object[]> rows = entityManager
                .createNativeQuery(CLEAR_WORK_TYPE)
                .unwrap(NativeQuery.class)
                .addScalar("id", StandardBasicTypes.LONG)
                .addScalar("status", StandardBasicTypes.STRING)
                .addScalar("technician_id", StandardBasicTypes.LONG)
                .setParameter("at", at)
                .setParameter("organizationId", organizationId.value())
                .setParameter("workTypeId", workTypeId.value())
                .setParameterList(
                        "statuses", statuses.stream().map(WorkStatus::name).toList())
                .getResultList();
        return rows.stream()
                .map(row -> new ClearedWorkType(
                        new WorkId((Long) row[0]),
                        WorkStatus.valueOf((String) row[1]),
                        row[2] == null ? null : new TechnicianId((Long) row[2])))
                .toList();
    }
}

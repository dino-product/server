package com.orbit.schedule.adapter.out.persistence;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.orbit.schedule.application.port.out.WorkQueryPort;
import com.orbit.schedule.application.port.out.WorkSearchCriteria;
import com.orbit.schedule.application.port.out.WorkSearchResult;
import com.orbit.schedule.domain.AssignmentResult;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.TechnicianId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.schedule.domain.WorkStatus;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.ComparableExpression;
import com.querydsl.core.types.dsl.DateTimeExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;

/**
 * {@link WorkQueryPort}의 JPA 구현. 결과는 Entity에서 새로 만든 Domain 객체라 영속 상태와 분리된 사본이다. 쪽을 나누지 않는 목록은 배정 이력을 함께 읽고
 * (fetch join), 사진은 지연 컬렉션의 일괄 조회({@code @BatchSize})로 읽어 작업마다 따로 읽는 N+1을 피한다.
 *
 * <ul>
 *   <li>종료시각은 저장 컬럼이 아니라 시작시각 + 예상소요시간이다. 구간 조회는 시작시각이 {@code [from - 예상소요시간 상한, to)}인 행을 시작시각 인덱스로
 *       좁힌 뒤 시작시각 + 예상소요시간이 {@code from}보다 늦은 행만 고른다.
 *   <li>일정 컬럼은 작업에 보일 일정이라({@link WorkMapper}) 등록(대기함) 행에도 미리 입력한 값이나 마지막 배정 일정이 있다. 기사 일정을 읽는 구간
 *       조회는 상태 조건을 함께 걸고, 작업 목록의 담당기사·기간 조건과 예정시간 정렬은 화면의 담당기사·예정시간 칸과 같도록 그대로 쓴다.
 *   <li>검색은 조건에 맞는 식별자로 쪽을 먼저 고른 뒤 그 작업들만 읽는다(컬렉션을 함께 읽은 채 쪽을 자르면 전체를 메모리에서 자르게 된다). 목록과 전체 수는
 *       따로 읽으므로 둘의 일관성은 호출 트랜잭션의 격리 수준에 따른다.
 * </ul>
 */
@Repository
class WorkQueryPersistenceAdapter implements WorkQueryPort {

    /** 끝나지 않아 지금 시각으로 지연을 판정하는 상태(활성과 등록). {@link Work#isDelayedAt} 참고. */
    private static final Set<WorkStatus> UNFINISHED_STATUSES = Arrays.stream(WorkStatus.values())
            .filter(status -> status.isActive() || status == WorkStatus.REGISTERED)
            .collect(Collectors.toUnmodifiableSet());
    /** 일정을 차지하거나 차지했던 상태(활성과 완료). 구간 조회가 담는다. */
    private static final Set<WorkStatus> SCHEDULED_STATUSES = Arrays.stream(WorkStatus.values())
            .filter(status -> status.isActive() || status == WorkStatus.COMPLETED)
            .collect(Collectors.toUnmodifiableSet());

    /** 보일 일정으로 구간에 담는 상태(일정 상태와 등록). */
    private static final Set<WorkStatus> SHOWN_STATUSES = Arrays.stream(WorkStatus.values())
            .filter(status -> status != WorkStatus.CANCELED)
            .collect(Collectors.toUnmodifiableSet());

    private static final char LIKE_ESCAPE = '\\';

    private static final QWorkJpaEntity WORK = QWorkJpaEntity.workJpaEntity;
    private static final QWorkAssignmentJpaEntity ASSIGNMENT = QWorkAssignmentJpaEntity.workAssignmentJpaEntity;

    /** 한꺼번에 읽는 목록에서 작업을 나눠 읽는 묶음 크기. */
    private static final int LOAD_CHUNK = 100;

    private final EntityManager entityManager;
    private final JPAQueryFactory queryFactory;

    WorkQueryPersistenceAdapter(EntityManager entityManager) {
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager must not be null");
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Work> listScheduledBetween(OrganizationId organizationId, Instant from, Instant to) {
        requirePeriod(organizationId, from, to);
        return currentSchedulesBetween(WORK.companyId.eq(organizationId.value()), from, to);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Work> listShownBetween(OrganizationId organizationId, Instant from, Instant to) {
        requirePeriod(organizationId, from, to);
        // 끝을 모르는(예상소요시간이 없는) 대기함 행은 종료 비교가 null이라 시작시각이 구간 안인지만 본다.
        return listWithAssignments(WORK.companyId
                .eq(organizationId.value())
                .and(WORK.status.in(SHOWN_STATUSES))
                .and(WORK.scheduledStartAt.goe(from.minus(WorkSchedule.MAX_EXPECTED_DURATION)))
                .and(WORK.scheduledStartAt.lt(to))
                .and(endsAfter(WORK.scheduledStartAt, WORK.expectedDuration, from)
                        .or(WORK.expectedDuration.isNull().and(WORK.scheduledStartAt.goe(from)))));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Work> listScheduledForTechnician(
            OrganizationId organizationId, TechnicianId technicianId, Instant from, Instant to) {
        requirePeriod(organizationId, from, to);
        Objects.requireNonNull(technicianId, "technicianId must not be null");
        return currentSchedulesBetween(
                WORK.companyId.eq(organizationId.value()).and(WORK.technicianId.eq(technicianId.value())), from, to);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Work> listAssignedToTechnician(
            OrganizationId organizationId, TechnicianId technicianId, Instant from, Instant to) {
        requirePeriod(organizationId, from, to);
        Objects.requireNonNull(technicianId, "technicianId must not be null");
        BooleanExpression assignedInPeriod = JPAExpressions.selectOne()
                .from(ASSIGNMENT)
                .where(
                        ASSIGNMENT.work.eq(WORK),
                        ASSIGNMENT.technicianId.eq(technicianId.value()),
                        ASSIGNMENT.scheduledStartAt.goe(from.minus(WorkSchedule.MAX_EXPECTED_DURATION)),
                        ASSIGNMENT.scheduledStartAt.lt(to),
                        endsAfter(ASSIGNMENT.scheduledStartAt, ASSIGNMENT.expectedDuration, from))
                .exists();
        return listWithAssignments(WORK.companyId.eq(organizationId.value()).and(assignedInPeriod));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Work> listBacklog(OrganizationId organizationId) {
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        return listWithAssignments(
                WORK.companyId.eq(organizationId.value()).and(WORK.status.eq(WorkStatus.REGISTERED)));
    }

    @Override
    @Transactional(readOnly = true)
    public WorkSearchResult search(OrganizationId organizationId, WorkSearchCriteria criteria) {
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(criteria, "criteria must not be null");
        BooleanBuilder condition = conditionOf(organizationId, criteria);
        List<Long> pageIds = queryFactory
                .select(WORK.id)
                .from(WORK)
                .where(condition)
                .orderBy(orderOf(criteria.sort()))
                .offset((long) criteria.page() * criteria.size())
                .limit(criteria.size())
                .fetch();
        return new WorkSearchResult(loadInOrder(pageIds), countOf(condition));
    }

    /** 묶음마다 Domain으로 옮긴 뒤 영속성 컨텍스트를 비워, 많은 작업을 읽어도 Entity가 트랜잭션 끝까지 쌓이지 않게 한다. */
    @Override
    @Transactional(readOnly = true)
    public List<WorkSearchResult.SearchedWork> listMatching(
            OrganizationId organizationId, WorkSearchCriteria criteria, int limit) {
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(criteria, "criteria must not be null");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        // 비우기는 아직 저장하지 않은 변경을 버리므로, 바깥 변경 트랜잭션에 합류한 호출은 막는다.
        if (!TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalStateException("listMatching must run in a read-only transaction");
        }
        List<Long> ids = queryFactory
                .select(WORK.id)
                .from(WORK)
                .where(conditionOf(organizationId, criteria))
                .orderBy(orderOf(criteria.sort()))
                .limit(limit)
                .fetch();
        List<WorkSearchResult.SearchedWork> works = new ArrayList<>(ids.size());
        for (int from = 0; from < ids.size(); from += LOAD_CHUNK) {
            works.addAll(loadInOrder(ids.subList(from, Math.min(from + LOAD_CHUNK, ids.size()))));
            entityManager.clear();
        }
        return works;
    }

    @Override
    @Transactional(readOnly = true)
    public long count(OrganizationId organizationId, WorkSearchCriteria criteria) {
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(criteria, "criteria must not be null");
        return countOf(conditionOf(organizationId, criteria));
    }

    private long countOf(Predicate condition) {
        Long total =
                queryFactory.select(WORK.count()).from(WORK).where(condition).fetchOne();
        return total == null ? 0 : total;
    }

    private List<Work> currentSchedulesBetween(BooleanExpression scope, Instant from, Instant to) {
        return listWithAssignments(scope.and(WORK.status.in(SCHEDULED_STATUSES))
                .and(WORK.scheduledStartAt.goe(from.minus(WorkSchedule.MAX_EXPECTED_DURATION)))
                .and(WORK.scheduledStartAt.lt(to))
                .and(endsAfter(WORK.scheduledStartAt, WORK.expectedDuration, from)));
    }

    /** 조건에 맞는 작업을 배정 이력과 함께 읽는다. 컬렉션을 하나만 fetch join하므로 Hibernate가 루트 Entity 중복을 없앤다. */
    private List<Work> listWithAssignments(Predicate condition) {
        return queryFactory.selectFrom(WORK).leftJoin(WORK.assignments).fetchJoin().where(condition).fetch().stream()
                .map(WorkMapper::toDomain)
                .toList();
    }

    private BooleanBuilder conditionOf(OrganizationId organizationId, WorkSearchCriteria criteria) {
        BooleanBuilder condition = new BooleanBuilder(WORK.companyId.eq(organizationId.value()));
        if (criteria.keyword() != null) {
            String pattern = "%" + escapeLike(criteria.keyword()) + "%";
            condition.and(WORK.name
                    .likeIgnoreCase(pattern, LIKE_ESCAPE)
                    .or(WORK.customerName.likeIgnoreCase(pattern, LIKE_ESCAPE))
                    .or(WORK.customerPhone.likeIgnoreCase(pattern, LIKE_ESCAPE))
                    .or(WORK.customerAddress.likeIgnoreCase(pattern, LIKE_ESCAPE))
                    .or(WORK.customerAddressDetail.likeIgnoreCase(pattern, LIKE_ESCAPE)));
        }
        if (!criteria.statuses().isEmpty()) {
            condition.and(WORK.status.in(criteria.statuses()));
        }
        if (criteria.technicianId() != null) {
            condition.and(WORK.technicianId.eq(criteria.technicianId().value()));
        }
        if (criteria.workTypeId() != null) {
            condition.and(WORK.jobTypeId.eq(criteria.workTypeId().value()));
        }
        if (criteria.registrarId() != null) {
            condition.and(WORK.createdBy.eq(criteria.registrarId().value()));
        }
        if (criteria.startFrom() != null) {
            condition
                    .and(WORK.scheduledStartAt.goe(criteria.startFrom()))
                    .and(WORK.scheduledStartAt.lt(criteria.startTo()));
        }
        if (criteria.returnedByRejectionOnly()) {
            condition.and(WORK.status.eq(WorkStatus.REGISTERED)).and(latestAssignmentRejected());
        }
        if (criteria.delayedAt() != null) {
            // 끝나지 않은 작업은 조회 시각, 완료한 작업은 완료 시각이 종료시각 이후면 지연이다. 시작시각이나 예상소요시간이 없으면 종료시각이 null이라 빠진다.
            condition.and(WORK.status
                    .in(UNFINISHED_STATUSES)
                    .and(endsNoLaterThan(Expressions.constant(criteria.delayedAt())))
                    .or(WORK.status.eq(WorkStatus.COMPLETED).and(endsNoLaterThan(WORK.completedAt))));
        }
        return condition;
    }

    private static BooleanExpression latestAssignmentRejected() {
        QWorkAssignmentJpaEntity latest = new QWorkAssignmentJpaEntity("latest");
        return JPAExpressions.selectOne()
                .from(ASSIGNMENT)
                .where(
                        ASSIGNMENT.work.eq(WORK),
                        ASSIGNMENT.result.eq(AssignmentResult.REJECTED),
                        ASSIGNMENT.id.eq(JPAExpressions.select(latest.id.max())
                                .from(latest)
                                .where(latest.work.eq(WORK))))
                .exists();
    }

    private static BooleanExpression endsNoLaterThan(Expression<Instant> instant) {
        return Expressions.booleanTemplate("{0} + {1} <= {2}", WORK.scheduledStartAt, WORK.expectedDuration, instant);
    }

    private static BooleanExpression endsAfter(
            DateTimeExpression<Instant> start, ComparableExpression<Duration> duration, Instant instant) {
        return Expressions.booleanTemplate("{0} + {1} > {2}", start, duration, Expressions.constant(instant));
    }

    private static OrderSpecifier<?>[] orderOf(WorkSearchCriteria.Sort sort) {
        OrderSpecifier<Long> tieBreaker = WORK.id.desc();
        return switch (sort) {
            case REGISTERED_DESC -> new OrderSpecifier<?>[] {WORK.registeredAt.desc(), tieBreaker};
            case REGISTERED_ASC -> new OrderSpecifier<?>[] {WORK.registeredAt.asc(), WORK.id.asc()};
            case START_TIME_ASC ->
                new OrderSpecifier<?>[] {WORK.scheduledStartAt.asc().nullsLast(), tieBreaker};
            case LAST_MODIFIED_DESC -> new OrderSpecifier<?>[] {WORK.updatedAt.desc(), tieBreaker};
        };
    }

    private List<WorkSearchResult.SearchedWork> loadInOrder(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        // 식별자로 이미 쪽을 골랐으므로 이력을 함께 읽어도 메모리에서 쪽을 자르지 않는다.
        Map<Long, WorkJpaEntity> byId =
                queryFactory
                        .selectFrom(WORK)
                        .leftJoin(WORK.assignments)
                        .fetchJoin()
                        .where(WORK.id.in(ids))
                        .fetch()
                        .stream()
                        .collect(Collectors.toMap(WorkJpaEntity::id, Function.identity()));
        return ids.stream()
                .map(byId::get)
                .map(entity -> new WorkSearchResult.SearchedWork(WorkMapper.toDomain(entity), entity.updatedAt()))
                .toList();
    }

    private static String escapeLike(String keyword) {
        StringBuilder escaped = new StringBuilder(keyword.length());
        for (char character : keyword.toCharArray()) {
            if (character == '%' || character == '_' || character == LIKE_ESCAPE) {
                escaped.append(LIKE_ESCAPE);
            }
            escaped.append(character);
        }
        return escaped.toString();
    }

    private static void requirePeriod(OrganizationId organizationId, Instant from, Instant to) {
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("from must precede to");
        }
    }
}

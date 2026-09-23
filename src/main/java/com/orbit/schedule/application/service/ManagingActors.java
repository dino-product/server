package com.orbit.schedule.application.service;

import java.util.Objects;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.domain.Actor;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.shared.error.BusinessException;

/**
 * 작업 관리(등록·기본정보 수정·배정 관리·취소)를 요청한 행위자를 확인하는 공통 절차. 작업 조회와 입력 검증보다 먼저 호출해, 권한 없는 요청에 작업 존재 여부나 입력
 * 오류가 드러나지 않게 한다. 확인 순서:
 *
 * <ol>
 *   <li>accountId 누락은 인증 계층의 프로그래밍 오류다.
 *   <li>조직 식별자 형식이 틀리면 400.
 *   <li>해당 조직의 활성 소속이 없으면 403(SCHEDULE-004).
 *   <li>포트가 요청과 다른 조직의 행위자를 돌려주면 프로그래밍 오류다.
 *   <li>작업 관리 역할(총관리자·직원)이 아니면 403(SCHEDULE-005).
 * </ol>
 */
final class ManagingActors {

    private ManagingActors() {}

    static Actor require(LoadActorPort loadActorPort, Long accountId, Long organizationIdValue) {
        Objects.requireNonNull(accountId, "accountId must not be null");
        OrganizationId organizationId = DomainRuleViolations.call(() -> new OrganizationId(organizationIdValue));
        Actor actor = loadActorPort
                .findActiveActor(accountId, organizationId)
                .orElseThrow(() -> new BusinessException(ScheduleErrorCode.NOT_ORGANIZATION_MEMBER));
        if (!actor.organizationId().equals(organizationId)) {
            throw new IllegalStateException("actor must belong to the requested organization");
        }
        if (!actor.canManageWorks()) {
            throw new BusinessException(ScheduleErrorCode.ACTION_NOT_ALLOWED);
        }
        return actor;
    }
}

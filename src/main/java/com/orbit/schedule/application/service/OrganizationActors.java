package com.orbit.schedule.application.service;

import java.util.Objects;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.domain.Actor;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.shared.error.BusinessException;

/**
 * 요청한 행위자를 조직·역할 수준에서 확인하는 공통 절차. 작업 조회와 입력 검증보다 먼저 호출해, 조직 구성원이 아니거나 역할이 맞지 않는 요청에 작업 존재 여부나
 * 입력 오류가 드러나지 않게 한다. 기사가 그 작업의 담당인지는 작업을 찾은 뒤 따로 확인한다. 확인 순서:
 *
 * <ol>
 *   <li>accountId 누락은 인증 계층의 프로그래밍 오류다.
 *   <li>조직 식별자 형식이 틀리면 400.
 *   <li>해당 조직의 활성 소속이 없으면 403(SCHEDULE-004).
 *   <li>포트가 요청과 다른 조직의 행위자를 돌려주면 프로그래밍 오류다.
 *   <li>유즈케이스의 역할(작업 관리는 총관리자·직원, 작업 수행은 기사)이 아니면 403(SCHEDULE-005).
 * </ol>
 */
final class OrganizationActors {

    private OrganizationActors() {}

    /** 작업 등록·기본정보 수정·배정 관리·취소를 요청한 총관리자·직원. */
    static Actor requireManager(LoadActorPort loadActorPort, Long accountId, Long organizationIdValue) {
        Actor actor = requireMember(loadActorPort, accountId, organizationIdValue);
        if (!actor.canManageWorks()) {
            throw new BusinessException(ScheduleErrorCode.ACTION_NOT_ALLOWED);
        }
        return actor;
    }

    /** 수락·거절·시작·완료보고를 요청한 기사. 그 작업의 담당 기사인지는 작업을 찾은 뒤 따로 확인한다. */
    static Actor requireTechnician(LoadActorPort loadActorPort, Long accountId, Long organizationIdValue) {
        Actor actor = requireMember(loadActorPort, accountId, organizationIdValue);
        if (!actor.canPerformWorks()) {
            throw new BusinessException(ScheduleErrorCode.ACTION_NOT_ALLOWED);
        }
        return actor;
    }

    private static Actor requireMember(LoadActorPort loadActorPort, Long accountId, Long organizationIdValue) {
        Objects.requireNonNull(accountId, "accountId must not be null");
        OrganizationId organizationId = DomainRuleViolations.call(() -> new OrganizationId(organizationIdValue));
        Actor actor = loadActorPort
                .findActiveActor(accountId, organizationId)
                .orElseThrow(() -> new BusinessException(ScheduleErrorCode.NOT_ORGANIZATION_MEMBER));
        if (!actor.organizationId().equals(organizationId)) {
            throw new IllegalStateException("actor must belong to the requested organization");
        }
        return actor;
    }
}

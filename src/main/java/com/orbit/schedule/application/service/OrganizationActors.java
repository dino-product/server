package com.orbit.schedule.application.service;

import java.util.Objects;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.domain.Actor;
import com.orbit.schedule.domain.ManagerActor;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.TechnicianActor;
import com.orbit.shared.error.BusinessException;

/**
 * 요청자를 조직·요청자 종류 수준에서 확인하는 공통 절차. 작업 조회와 입력 검증보다 먼저 호출해, 조직의 활성 요청자가 아니거나 종류가 맞지 않는 요청에 작업 존재 여부나
 * 입력 오류가 드러나지 않게 한다. 기사가 그 작업의 담당인지는 작업을 찾은 뒤 따로 확인한다. 확인 순서:
 *
 * <ol>
 *   <li>accountId 누락은 인증 계층의 프로그래밍 오류다.
 *   <li>조직 식별자 형식이 틀리면 400.
 *   <li>해당 조직의 활성 관리자(소속)도 기사(기사 계약)도 아니면 403(SCHEDULE-004).
 *   <li>포트가 요청과 다른 조직의 행위자를 돌려주면 프로그래밍 오류다.
 *   <li>유즈케이스의 요청자 종류(작업 관리는 관리자, 작업 수행은 기사)가 아니면 403(SCHEDULE-005).
 * </ol>
 */
final class OrganizationActors {

    private OrganizationActors() {}

    /** 작업 등록·기본정보 수정·배정 관리·취소를 요청한 총관리자·직원. */
    static ManagerActor requireManager(LoadActorPort loadActorPort, Long accountId, Long organizationIdValue) {
        if (requireActor(loadActorPort, accountId, organizationIdValue) instanceof ManagerActor manager) {
            return manager;
        }
        throw new BusinessException(ScheduleErrorCode.ACTION_NOT_ALLOWED);
    }

    /** 수락·거절·시작·완료보고를 요청한 기사. 그 작업의 담당 기사인지는 작업을 찾은 뒤 따로 확인한다. */
    static TechnicianActor requireTechnician(LoadActorPort loadActorPort, Long accountId, Long organizationIdValue) {
        if (requireActor(loadActorPort, accountId, organizationIdValue) instanceof TechnicianActor technician) {
            return technician;
        }
        throw new BusinessException(ScheduleErrorCode.ACTION_NOT_ALLOWED);
    }

    private static Actor requireActor(LoadActorPort loadActorPort, Long accountId, Long organizationIdValue) {
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

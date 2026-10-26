package com.orbit.organization.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.WorkTypeDeleted;
import com.orbit.organization.WorkTypeRelease;
import com.orbit.organization.WorkTypeReleasedWork;
import com.orbit.organization.application.port.in.command.DeleteWorkTypeUseCase;
import com.orbit.organization.application.port.in.command.dto.DeleteWorkTypeCommand;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.application.port.out.WorkTypeRepository;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.WorkType;
import com.orbit.organization.domain.WorkTypeId;

/**
 * 작업 유형 소프트 삭제. 같은 트랜잭션에서 schedule이 시작 전 작업의 유형을 비우고, 비운 작업을 담아 삭제 이벤트를 발행한다. 시작한 작업은 삭제된 유형을 그대로
 * 참조한다([조직·계정] §7). 이미 삭제된 유형은 아무것도 바꾸지 않고 성공한다.
 *
 * <p>순서: 유형 행을 삭제 표시로 바꿔 저장(flush)한 뒤 해제를 부른다. 작업에 유형을 지정하는 쪽이 {@code WorkTypeLookup#isSelectable}로 같은 행을 공유
 * 잠금으로 잡으므로, 삭제는 확인을 마친 작업의 커밋을 기다린 뒤 해제해 그 작업까지 비우고, 이후의 지정은 삭제된 유형으로 거부된다. 해제가 기다린 뒤 커밋된 작업을
 * 보도록 기본 격리 수준(READ COMMITTED)에서 실행한다.
 */
@Service
public class DeleteWorkTypeService implements DeleteWorkTypeUseCase {

    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;
    private final WorkTypeRepository workTypeRepository;
    private final WorkTypeRelease workTypeRelease;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public DeleteWorkTypeService(
            OrganizationRepository organizationRepository,
            MembershipRepository membershipRepository,
            WorkTypeRepository workTypeRepository,
            WorkTypeRelease workTypeRelease,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.workTypeRepository = workTypeRepository;
        this.workTypeRelease = workTypeRelease;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void delete(DeleteWorkTypeCommand command) {
        Objects.requireNonNull(command.accountId(), "accountId must not be null");
        WorkTypeId workTypeId = WorkTypes.id(command.workTypeId());
        OrganizationId organizationId =
                OrganizationOwners.require(membershipRepository, command.accountId(), command.organizationId());

        OrganizationLocks.lock(organizationRepository, organizationId);
        WorkType workType = WorkTypes.find(workTypeRepository, organizationId, workTypeId);
        Instant now = clock.instant();
        if (!workType.delete(now)) {
            return;
        }
        workTypeRepository.save(workType);
        List<WorkTypeReleasedWork> released =
                workTypeRelease.releaseForDeletion(organizationId.value(), workTypeId.value());
        eventPublisher.publishEvent(new WorkTypeDeleted(organizationId.value(), workTypeId.value(), released, now));
    }
}

package com.orbit.schedule.application.service;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.media.MediaFiles;
import com.orbit.media.StagedFiles;
import com.orbit.schedule.application.port.in.command.dto.UpdateWorkDetailsCommand;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.application.port.out.WorkTypePort;
import com.orbit.schedule.domain.CustomerInfo;
import com.orbit.schedule.domain.ManagerActor;
import com.orbit.schedule.domain.Money;
import com.orbit.schedule.domain.PaymentInfo;
import com.orbit.schedule.domain.SiteAddress;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkTypeId;

/**
 * 기본정보 수정의 트랜잭션 부분({@link UpdateWorkDetailsService}가 사진 확인을 마친 뒤 부른다). 현장 사진의 확인 결과는 다른 입력과 함께 검증하고, 작업을 저장한 같은
 * 트랜잭션에서 보낸 사진을 그 작업의 현장 사진 전체로 확정한다(빠진 사진은 만료).
 */
@Component
public class UpdateWorkDetailsTransaction {

    private final LoadActorPort loadActorPort;
    private final WorkRepository workRepository;
    private final WorkTypePort workTypePort;
    private final MediaFiles mediaFiles;

    public UpdateWorkDetailsTransaction(
            LoadActorPort loadActorPort,
            WorkRepository workRepository,
            WorkTypePort workTypePort,
            MediaFiles mediaFiles) {
        this.loadActorPort = loadActorPort;
        this.workRepository = workRepository;
        this.workTypePort = workTypePort;
        this.mediaFiles = mediaFiles;
    }

    /** @param sitePhotos 트랜잭션 밖에서 확인한 현장 사진 */
    @Transactional
    public void update(UpdateWorkDetailsCommand command, StagedFiles sitePhotos) {
        ManagerActor actor =
                OrganizationActors.requireManager(loadActorPort, command.accountId(), command.organizationId());
        Work work = OrganizationWorks.require(workRepository, actor.organizationId(), command.workId());
        OrganizationActors.requireRegistrarOrOwner(actor, work);
        Optional<WorkTypeId> workTypeBefore = work.workType();

        DomainRuleViolations.run(() -> work.changeDetails(
                command.name(),
                command.workTypeId() == null ? null : new WorkTypeId(command.workTypeId()),
                new CustomerInfo(
                        command.customerName(),
                        command.customerPhone(),
                        new SiteAddress(
                                command.customerPostalCode(),
                                command.customerAddress(),
                                command.customerAddressDetail())),
                new PaymentInfo(command.fee() == null ? null : new Money(command.fee()), command.paymentMethod()),
                command.sitePhotoIds()));
        WorkPhotos.requireAccepted(sitePhotos);
        // 지금 유형을 그대로 보낸 수정은 다시 확인하지 않는다. 작업중 작업이 그 사이 삭제된 유형을 가져도 다른 항목을 고칠 수 있다.
        if (!work.workType().equals(workTypeBefore)) {
            OrganizationReferences.requireSelectableWorkType(workTypePort, work);
        }

        workRepository.save(work);
        WorkPhotos.attach(mediaFiles, sitePhotos, work.id().orElseThrow());
    }
}

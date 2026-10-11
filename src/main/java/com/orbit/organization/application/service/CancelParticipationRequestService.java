package com.orbit.organization.application.service;

import java.time.Clock;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.in.command.CancelParticipationRequestUseCase;
import com.orbit.organization.application.port.in.command.dto.CancelParticipationRequestCommand;
import com.orbit.organization.application.port.in.command.dto.ParticipationRequestInfo;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.application.port.out.ParticipationRequestRepository;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.Organization;
import com.orbit.organization.domain.ParticipationRequest;
import com.orbit.organization.domain.ParticipationRequestId;
import com.orbit.shared.error.BusinessException;
import com.orbit.shared.error.CommonErrorCode;

/**
 * 요청자 본인의 승인 대기 요청을 취소한다. 취소는 알림을 만들지 않는다. 없는 요청과 다른 계정의 요청은 구분하지 않고 거부해 요청 존재 여부를 드러내지 않는다. 요청 행을
 * 잠그고 읽어 승인·거절과 동시에 와도 먼저 커밋한 쪽만 반영된다.
 */
@Service
public class CancelParticipationRequestService implements CancelParticipationRequestUseCase {

    private final ParticipationRequestRepository requestRepository;
    private final OrganizationRepository organizationRepository;
    private final Clock clock;

    public CancelParticipationRequestService(
            ParticipationRequestRepository requestRepository,
            OrganizationRepository organizationRepository,
            Clock clock) {
        this.requestRepository = requestRepository;
        this.organizationRepository = organizationRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ParticipationRequestInfo cancel(CancelParticipationRequestCommand command) {
        AccountId requester = new AccountId(Objects.requireNonNull(command.accountId(), "accountId must not be null"));
        ParticipationRequest request = requestRepository
                .findByIdForUpdate(requestId(command.requestId()))
                .filter(found -> found.isRequestedBy(requester))
                .orElseThrow(() -> new BusinessException(OrganizationErrorCode.NOT_REQUESTER));
        if (!request.isPending()) {
            throw new BusinessException(OrganizationErrorCode.REQUEST_NOT_PENDING);
        }

        request.cancel(clock.instant());
        ParticipationRequest saved = requestRepository.save(request);
        Organization organization = organizationRepository
                .findById(saved.organizationId())
                .orElseThrow(() -> new IllegalStateException("participation request must belong to an organization"));
        return ParticipationRequestInfo.of(saved, organization);
    }

    private static ParticipationRequestId requestId(Long value) {
        try {
            return new ParticipationRequestId(value);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, exception);
        }
    }
}

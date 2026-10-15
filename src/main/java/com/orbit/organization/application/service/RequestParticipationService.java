package com.orbit.organization.application.service;

import java.time.Clock;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.in.command.RequestParticipationUseCase;
import com.orbit.organization.application.port.in.command.dto.ParticipationRequestInfo;
import com.orbit.organization.application.port.in.command.dto.RequestParticipationCommand;
import com.orbit.organization.application.port.in.query.dto.ParticipationAvailability;
import com.orbit.organization.application.port.out.AccountMembershipQueryPort;
import com.orbit.organization.application.port.out.CompanyCodeAttemptLimiter;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.application.port.out.ParticipationRequestRepository;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.Organization;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.ParticipationRequest;
import com.orbit.shared.error.BusinessException;
import com.orbit.shared.error.CommonErrorCode;

/**
 * 회사 코드로 확인한 발주사에 승인 대기 요청을 만든다. 발주사 행을 잠근 뒤 코드를 다시 확인하고 참여 여부를 판정해, 같은 계정의 동시 요청은 승인 대기 요청 하나만
 * 남기고 대기 중 바뀐 회사 코드는 이전 코드로 거부한다. 다른 발주사에는 동시에 요청할 수 있다.
 */
@Service
public class RequestParticipationService implements RequestParticipationUseCase {

    private final OrganizationRepository organizationRepository;
    private final AccountMembershipQueryPort membershipQueryPort;
    private final ParticipationRequestRepository requestRepository;
    private final CompanyCodeAttemptLimiter attemptLimiter;
    private final Clock clock;

    public RequestParticipationService(
            OrganizationRepository organizationRepository,
            AccountMembershipQueryPort membershipQueryPort,
            ParticipationRequestRepository requestRepository,
            CompanyCodeAttemptLimiter attemptLimiter,
            Clock clock) {
        this.organizationRepository = organizationRepository;
        this.membershipQueryPort = membershipQueryPort;
        this.requestRepository = requestRepository;
        this.attemptLimiter = attemptLimiter;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ParticipationRequestInfo request(RequestParticipationCommand command) {
        AccountId requester = new AccountId(Objects.requireNonNull(command.accountId(), "accountId must not be null"));
        if (command.desiredType() == null) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST);
        }
        organizationRepository.lock(organizationIdOf(
                CompanyCodes.resolve(organizationRepository, attemptLimiter, requester, command.companyCode())));
        Organization organization =
                CompanyCodes.resolve(organizationRepository, attemptLimiter, requester, command.companyCode());
        OrganizationId organizationId = organizationIdOf(organization);

        ParticipationEligibility eligibility =
                ParticipationEligibility.evaluate(membershipQueryPort, requestRepository, organizationId, requester);
        if (eligibility.availability() != ParticipationAvailability.AVAILABLE) {
            throw new BusinessException(rejection(eligibility.availability()));
        }

        ParticipationRequest saved = requestRepository.save(
                ParticipationRequest.request(organizationId, requester, command.desiredType(), clock.instant()));
        return ParticipationRequestInfo.of(saved, organization);
    }

    private static OrganizationErrorCode rejection(ParticipationAvailability availability) {
        return switch (availability) {
            case ALREADY_MEMBER -> OrganizationErrorCode.ALREADY_MEMBER;
            case DEACTIVATED_MEMBER -> OrganizationErrorCode.DEACTIVATED_MEMBER;
            case PENDING_REQUEST -> OrganizationErrorCode.PENDING_REQUEST_EXISTS;
            case AVAILABLE -> throw new IllegalArgumentException("available participation is not a rejection");
        };
    }

    private static OrganizationId organizationIdOf(Organization organization) {
        return organization.id().orElseThrow(() -> new IllegalStateException("stored organization must have an id"));
    }
}

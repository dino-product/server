package com.orbit.organization.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.application.port.in.command.DeactivateWithdrawnAccountUseCase;
import com.orbit.organization.application.port.in.command.dto.DeactivateWithdrawnAccountCommand;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.application.port.out.TechnicianRepository;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.OrganizationId;

/**
 * 탈퇴가 커밋된 계정의 모든 발주사 직원 소속(총관리자 포함)과 기사 계약을 비활성으로 바꾸고 탈퇴 표시를 남긴다([조직·계정] 정책 §5.1, 기능명세 §6). 이미
 * 비활성인 행에도 탈퇴 표시를 남겨, 탈퇴한 계정의 행은 재활성화·재참여 승인으로 되살아나지 않는다.
 *
 * <p>행이 있는 발주사를 식별자 오름차순으로 하나씩 잠근 뒤 행을 읽어 바꾼다. 총관리자 지정·재참여 승인처럼 발주사를 잠그고 Entity를 통째로 다시 쓰는
 * 유즈케이스가 이 변경을 되돌리지 않고, 여러 발주사를 잠가도 교착하지 않는다. 대기 한도를 넘기면 409(ORGANIZATION-010)로 실패해 다시 받게 한다. 탈퇴
 * 표시가 있는 행은 건너뛰므로 같은 탈퇴를 다시 받아도 결과가 같다. 기사의 예정 작업 반환은 schedule이 같은 탈퇴를 받아 따로 하므로 여기서
 * {@code TechnicianWorkRelease}를 부르지 않는다.
 */
@Service
public class DeactivateWithdrawnAccountService implements DeactivateWithdrawnAccountUseCase {

    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;
    private final TechnicianRepository technicianRepository;
    private final Clock clock;

    public DeactivateWithdrawnAccountService(
            OrganizationRepository organizationRepository,
            MembershipRepository membershipRepository,
            TechnicianRepository technicianRepository,
            Clock clock) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.technicianRepository = technicianRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void deactivate(DeactivateWithdrawnAccountCommand command) {
        AccountId accountId = new AccountId(command.accountId());
        Instant now = clock.instant();
        for (OrganizationId organizationId : organizationsOf(accountId)) {
            OrganizationLocks.lock(organizationRepository, organizationId);
            membershipRepository
                    .find(organizationId, accountId)
                    .filter(membership -> !membership.isWithdrawn())
                    .ifPresent(membership -> membershipRepository.withdraw(membership.withdraw(now)));
            technicianRepository
                    .find(organizationId, accountId)
                    .filter(technician -> !technician.isWithdrawn())
                    .ifPresent(technician -> technicianRepository.withdraw(technician.withdraw(now)));
        }
    }

    private List<OrganizationId> organizationsOf(AccountId accountId) {
        return Stream.concat(
                        membershipRepository.listOrganizationsOf(accountId).stream(),
                        technicianRepository.listOrganizationsOf(accountId).stream())
                .distinct()
                .sorted(Comparator.comparing(OrganizationId::value))
                .toList();
    }
}

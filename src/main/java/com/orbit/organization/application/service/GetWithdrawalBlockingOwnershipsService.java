package com.orbit.organization.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.application.port.in.query.GetWithdrawalBlockingOwnershipsUseCase;
import com.orbit.organization.application.port.in.query.dto.GetWithdrawalBlockingOwnershipsQuery;
import com.orbit.organization.application.port.in.query.dto.WithdrawalBlockingOwnershipsInfo;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.OrganizationId;

/**
 * 계정 탈퇴를 막는 유일한 총관리자 판정([조직·계정] 정책 §5.1 ①). 계정이 활성 총관리자인 발주사를 식별자 오름차순으로 하나씩 잠그고, 잠근 뒤 그 계정의 소속과
 * 활성 총관리자 수를 다시 읽어 센다. 그래서 판정과 탈퇴 커밋 사이에 다른 총관리자가 해제되지 않고, 여러 발주사를 함께 잠가도 다른 판정과 교착하지 않는다. 상태는
 * 바꾸지 않는다. 잠금을 잡으므로 읽기 전용 트랜잭션으로 두지 않는다. 대기 한도를 넘기면 409(ORGANIZATION-010)다.
 */
@Service
public class GetWithdrawalBlockingOwnershipsService implements GetWithdrawalBlockingOwnershipsUseCase {

    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;

    public GetWithdrawalBlockingOwnershipsService(
            OrganizationRepository organizationRepository, MembershipRepository membershipRepository) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
    }

    @Override
    @Transactional
    public WithdrawalBlockingOwnershipsInfo get(GetWithdrawalBlockingOwnershipsQuery query) {
        AccountId accountId = new AccountId(query.accountId());
        int soleOwnerOrganizations = 0;
        for (OrganizationId organizationId : membershipRepository.listOwnedOrganizations(accountId)) {
            OrganizationLocks.lock(organizationRepository, organizationId);
            long activeOwners = membershipRepository.countActiveOwners(organizationId);
            boolean soleOwner = membershipRepository
                    .findActive(organizationId, accountId)
                    .filter(membership -> membership.isSoleOwner(activeOwners))
                    .isPresent();
            if (soleOwner) {
                soleOwnerOrganizations++;
            }
        }
        return new WithdrawalBlockingOwnershipsInfo(soleOwnerOrganizations);
    }
}

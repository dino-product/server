package com.orbit.organization.application.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.application.port.in.query.ListMyMembershipsUseCase;
import com.orbit.organization.application.port.in.query.dto.ListMyMembershipsQuery;
import com.orbit.organization.application.port.in.query.dto.MyMembershipInfo;
import com.orbit.organization.application.port.out.AccountMembership;
import com.orbit.organization.application.port.out.AccountMembershipQueryPort;
import com.orbit.organization.domain.AccountId;

/**
 * 역할 변경은 기존 행을 비활성으로 남기므로 한 발주사에 직원 소속·기사 계약 두 행이 있을 수 있다. 목록은 발주사마다 한 줄로, 활성 행이 있으면 그 행, 없으면 상태가 가장
 * 늦게 바뀐 비활성 행을 보인다. 순서는 발주사에 처음 참여한 시각순이며 역할을 바꿔도 유지된다.
 */
@Service
public class ListMyMembershipsService implements ListMyMembershipsUseCase {

    private static final Comparator<AccountMembership> REPRESENTATIVE =
            Comparator.comparing(AccountMembership::active).thenComparing(AccountMembership::statusChangedAt);

    private static final Comparator<List<AccountMembership>> FIRST_JOINED = Comparator.comparing(
                    ListMyMembershipsService::firstJoinedAt)
            .thenComparing(rows -> rows.getFirst().organizationId().value());

    private final AccountMembershipQueryPort accountMembershipQueryPort;

    public ListMyMembershipsService(AccountMembershipQueryPort accountMembershipQueryPort) {
        this.accountMembershipQueryPort = accountMembershipQueryPort;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MyMembershipInfo> list(ListMyMembershipsQuery query) {
        return accountMembershipQueryPort.listMemberships(new AccountId(query.accountId())).stream()
                .collect(Collectors.groupingBy(AccountMembership::organizationId))
                .values()
                .stream()
                .sorted(FIRST_JOINED)
                .map(ListMyMembershipsService::representative)
                .map(ListMyMembershipsService::toInfo)
                .toList();
    }

    private static AccountMembership representative(List<AccountMembership> rows) {
        if (rows.stream().filter(AccountMembership::active).count() > 1) {
            throw new IllegalStateException("account must not have both active membership and technician contract");
        }
        return rows.stream().max(REPRESENTATIVE).orElseThrow();
    }

    private static Instant firstJoinedAt(List<AccountMembership> rows) {
        return rows.stream()
                .map(AccountMembership::joinedAt)
                .min(Comparator.naturalOrder())
                .orElseThrow();
    }

    private static MyMembershipInfo toInfo(AccountMembership row) {
        return new MyMembershipInfo(row.organizationId().value(), row.organizationName(), row.role(), row.active());
    }
}

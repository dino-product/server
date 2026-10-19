package com.orbit.organization.application.service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.application.port.in.query.ListParticipantsUseCase;
import com.orbit.organization.application.port.in.query.dto.ListParticipantsQuery;
import com.orbit.organization.application.port.in.query.dto.ParticipantInfo;
import com.orbit.organization.application.port.in.query.dto.ParticipantsInfo;
import com.orbit.organization.application.port.out.AccountNamesPort;
import com.orbit.organization.application.port.out.MembershipRepository;
import com.orbit.organization.application.port.out.ParticipantQueryPort;
import com.orbit.organization.application.port.out.ParticipantRow;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.MemberRole;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.ParticipationType;

/**
 * 그 발주사의 활성 총관리자에게 직원 소속·기사 계약을 계정마다 한 줄로 보여 준다. 역할 변경은 기존 행을 비활성으로 남기므로, 활성 행이 있으면 그 행, 없으면 상태가
 * 가장 늦게 바뀐 행(같으면 직원 소속, 재참여 승인의 마지막 역할 판정과 같음)을 보인다. 비활성 구성원도 보인다.
 *
 * <p>순서는 이름 가나다순, 같으면 참여일이 오래된 순이며 이름이 없는 계정은 맨 뒤다. 이름 검색은 앞뒤 공백을 지운 검색어를 대소문자 구분 없이 부분 일치로 찾고,
 * 역할별 인원 수는 검색을 적용한 뒤 역할 필터와 관계없이 센다. 이름은 profile이 소유하므로 검색·정렬은 이름을 붙인 뒤 메모리에서 한다.
 */
@Service
public class ListParticipantsService implements ListParticipantsUseCase {

    private static final Comparator<ParticipantRow> REPRESENTATIVE = Comparator.comparing(ParticipantRow::active)
            .thenComparing(ParticipantRow::statusChangedAt)
            .thenComparing(row -> row.role() != MemberRole.TECHNICIAN);

    private static final Comparator<ParticipantInfo> DISPLAY_ORDER = Comparator.comparing(
                    ParticipantInfo::name, Comparator.nullsLast(Comparator.<String>naturalOrder()))
            .thenComparing(ParticipantInfo::joinedAt)
            .thenComparing(ParticipantInfo::accountId);

    private final MembershipRepository membershipRepository;
    private final ParticipantQueryPort queryPort;
    private final AccountNamesPort accountNames;

    public ListParticipantsService(
            MembershipRepository membershipRepository, ParticipantQueryPort queryPort, AccountNamesPort accountNames) {
        this.membershipRepository = membershipRepository;
        this.queryPort = queryPort;
        this.accountNames = accountNames;
    }

    @Override
    @Transactional(readOnly = true)
    public ParticipantsInfo list(ListParticipantsQuery query) {
        OrganizationId organizationId =
                OrganizationOwners.require(membershipRepository, query.accountId(), query.organizationId());
        List<ParticipantRow> rows = queryPort.listParticipants(organizationId);
        if (rows.isEmpty()) {
            return new ParticipantsInfo(new ParticipantsInfo.Counts(0, 0, 0), List.of());
        }
        Map<AccountId, String> names = accountNames.findNames(
                rows.stream().map(ParticipantRow::accountId).collect(Collectors.toSet()));
        String keyword = keyword(query.name());
        List<ParticipantInfo> searched =
                rows.stream().collect(Collectors.groupingBy(ParticipantRow::accountId)).values().stream()
                        .map(ListParticipantsService::representative)
                        .map(row -> toInfo(row, names.get(row.accountId())))
                        .filter(participant -> matches(participant, keyword))
                        .sorted(DISPLAY_ORDER)
                        .toList();
        List<ParticipantInfo> filtered = searched.stream()
                .filter(participant -> query.type() == null || typeOf(participant.role()) == query.type())
                .toList();
        return new ParticipantsInfo(counts(searched), filtered);
    }

    private static ParticipantRow representative(List<ParticipantRow> rows) {
        if (rows.stream().filter(ParticipantRow::active).count() > 1) {
            throw new IllegalStateException("account must not have both active membership and technician contract");
        }
        return rows.stream().max(REPRESENTATIVE).orElseThrow();
    }

    private static String keyword(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return name.strip().toLowerCase(Locale.ROOT);
    }

    private static boolean matches(ParticipantInfo participant, String keyword) {
        if (keyword == null) {
            return true;
        }
        return participant.name() != null
                && participant.name().toLowerCase(Locale.ROOT).contains(keyword);
    }

    private static ParticipationType typeOf(MemberRole role) {
        return role == MemberRole.TECHNICIAN ? ParticipationType.TECHNICIAN : ParticipationType.STAFF;
    }

    private static ParticipantsInfo.Counts counts(List<ParticipantInfo> participants) {
        int technicians = (int) participants.stream()
                .filter(participant -> participant.role() == MemberRole.TECHNICIAN)
                .count();
        return new ParticipantsInfo.Counts(participants.size(), participants.size() - technicians, technicians);
    }

    private static ParticipantInfo toInfo(ParticipantRow row, String name) {
        ParticipantInfo.Category category = row.categoryId() == null
                ? null
                : new ParticipantInfo.Category(row.categoryId(), row.categoryName(), row.categoryColor());
        return new ParticipantInfo(
                row.memberId(), row.accountId().value(), name, row.role(), row.active(), category, row.joinedAt());
    }
}

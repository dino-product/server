package com.orbit.organization.adapter.in.web;

import java.util.List;

import com.orbit.organization.application.port.in.query.dto.MyMembershipInfo;
import com.orbit.organization.domain.MemberRole;

import io.swagger.v3.oas.annotations.media.Schema;

public record MyMembershipsResponse(
        @Schema(description = "발주사마다 한 줄. 발주사에 처음 참여한 순서") List<Membership> memberships) {

    static MyMembershipsResponse from(List<MyMembershipInfo> memberships) {
        return new MyMembershipsResponse(
                memberships.stream().map(Membership::from).toList());
    }

    public record Membership(
            @Schema(description = "발주사 식별자", example = "1") Long organizationId,

            @Schema(description = "발주사명", example = "오르빗 설비")
            String organizationName,

            @Schema(description = "역할. OWNER 총관리자, STAFF 직원, TECHNICIAN 기사", example = "OWNER")
            MemberRole role,

            @Schema(description = "소속 상태. ACTIVE 활성, DEACTIVATED 비활성(이 발주사 기능을 쓸 수 없음)", example = "ACTIVE")
            Status status) {

        static Membership from(MyMembershipInfo membership) {
            return new Membership(
                    membership.organizationId(),
                    membership.organizationName(),
                    membership.role(),
                    membership.active() ? Status.ACTIVE : Status.DEACTIVATED);
        }
    }

    public enum Status {
        ACTIVE,
        DEACTIVATED
    }
}

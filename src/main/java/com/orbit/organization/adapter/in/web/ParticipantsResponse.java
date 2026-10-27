package com.orbit.organization.adapter.in.web;

import java.time.Instant;
import java.util.List;

import com.orbit.organization.application.port.in.query.dto.ParticipantInfo;
import com.orbit.organization.application.port.in.query.dto.ParticipantsInfo;
import com.orbit.organization.domain.MemberRole;

import io.swagger.v3.oas.annotations.media.Schema;

public record ParticipantsResponse(
        @Schema(description = "역할별 인원 수. 이름 검색을 적용한 뒤 역할 필터와 관계없이 센다(비활성 포함)")
        Counts counts,

        @Schema(description = "계정마다 한 줄. 이름 가나다순(영문은 대소문자 구분 없이, 같으면 참여일 오래된 순), 이름이 없는 계정은 맨 뒤")
        List<Participant> participants) {

    static ParticipantsResponse from(ParticipantsInfo info) {
        return new ParticipantsResponse(
                Counts.from(info.counts()),
                info.participants().stream().map(Participant::from).toList());
    }

    public record Counts(
            @Schema(description = "전체", example = "12") int all,

            @Schema(description = "사무실 직원(총관리자 포함)", example = "5")
            int staff,

            @Schema(description = "기사", example = "7") int technician) {

        static Counts from(ParticipantsInfo.Counts counts) {
            return new Counts(counts.all(), counts.staff(), counts.technician());
        }
    }

    public record Participant(
            @Schema(description = "역할이 OWNER·STAFF면 직원 소속 식별자, TECHNICIAN이면 기사 계약 식별자", example = "3")
            Long memberId,

            @Schema(description = "계정 식별자", example = "42") Long accountId,

            @Schema(description = "이름. 가입을 마치지 않았거나 프로필이 없으면 null", example = "홍길동")
            String name,

            @Schema(description = "역할. OWNER 총관리자, STAFF 직원, TECHNICIAN 기사", example = "TECHNICIAN")
            MemberRole role,

            @Schema(description = "세부 유형. 역할이 OWNER·STAFF면 직원 유형, TECHNICIAN이면 기사 유형이며 \"유형 없음\"이면 null")
            Category category,

            @Schema(description = "소속 상태. ACTIVE 활성, DEACTIVATED 비활성(로그인·배정 대상에서 빠짐)", example = "ACTIVE")
            Status status,

            @Schema(
                    description = "참여일(UTC). 발주사를 만들었거나 승인된 시각이며 재활성화·역할 변경에도 바뀌지 않는다",
                    example = "2026-10-01T00:00:00Z")
            Instant joinedAt) {

        static Participant from(ParticipantInfo info) {
            return new Participant(
                    info.memberId(),
                    info.accountId(),
                    info.name(),
                    info.role(),
                    info.category() == null ? null : Category.from(info.category()),
                    info.active() ? Status.ACTIVE : Status.DEACTIVATED,
                    info.joinedAt());
        }
    }

    public record Category(
            @Schema(description = "유형 식별자", example = "1") Long categoryId,

            @Schema(description = "유형 이름", example = "전기 기사")
            String name,

            @Schema(description = "색상(대문자 #RRGGBB)", example = "#3B82F6")
            String color) {

        static Category from(ParticipantInfo.Category category) {
            return new Category(category.categoryId(), category.name(), category.color());
        }
    }

    public enum Status {
        ACTIVE,
        DEACTIVATED
    }
}

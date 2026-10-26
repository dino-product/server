package com.orbit.organization.adapter.in.web;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

/** 중복·0 이하 식별자는 서비스가, 사유 길이는 도메인이 거부한다(Bean Validation과 글자 수 세는 법이 달라 여기에 두지 않는다). */
public record RejectParticipationRequestsRequest(
        @ArraySchema(
                arraySchema =
                        @Schema(
                                description = "거절할 참여 요청 식별자. 한 건만 거절해도 목록으로 보낸다",
                                requiredMode = Schema.RequiredMode.REQUIRED),
                schema = @Schema(example = "3"),
                minItems = 1,
                maxItems = 100)
        @NotEmpty
        @Size(max = 100)
        List<@NotNull Long> requestIds,

        @Schema(
                description = "거절 사유(선택). 앞뒤 공백을 지우고 최대 255자. 비우면 사유 없음. 신청자의 거절 알림·화면에 보인다. "
                        + "여러 건을 함께 거절하면 모두 같은 사유가 남는다",
                example = "이번 달 채용이 마감되었습니다")
        String reason) {}

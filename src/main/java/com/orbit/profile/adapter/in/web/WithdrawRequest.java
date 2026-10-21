package com.orbit.profile.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;

public record WithdrawRequest(
        @Schema(
                description = "탈퇴 사유. 정해진 선택지 없는 자유 텍스트이며 비워도 된다. 앞뒤 공백을 지운 뒤 200자까지",
                example = "더 이상 사용하지 않아요",
                maxLength = 200)
        String reason) {}

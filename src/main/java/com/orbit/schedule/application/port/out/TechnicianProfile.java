package com.orbit.schedule.application.port.out;

import com.orbit.schedule.domain.TechnicianId;

/**
 * 기사 계약의 표시 정보.
 *
 * @param technicianId 기사 계약 식별자
 * @param name 기사 이름(계정 이름). 가입을 마치지 않았거나 탈퇴해 이름을 찾을 수 없으면 null이며, 그때의 표기는 화면이 정한다
 * @param technicianType 기사유형. 유형이 없거나 삭제됐으면 null(삭제된 기사유형은 소속을 유형 없음으로 둔다)
 */
public record TechnicianProfile(TechnicianId technicianId, String name, TypeLabel technicianType) {

    public TechnicianProfile {
        if (technicianId == null) {
            throw new IllegalArgumentException("technicianId must not be null");
        }
    }
}

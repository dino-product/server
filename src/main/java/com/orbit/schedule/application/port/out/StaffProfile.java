package com.orbit.schedule.application.port.out;

import com.orbit.schedule.domain.MembershipId;

/**
 * 직원 소속(총관리자·직원)의 표시 정보. 작업의 등록자 표기에 쓴다.
 *
 * @param membershipId 소속 식별자
 * @param name 이름(계정 이름). 가입을 마치지 않았거나 탈퇴해 이름을 찾을 수 없으면 null이며, 그때의 표기는 화면이 정한다
 * @param owner 총관리자인지
 * @param staffType 직원유형. 유형이 없으면 null
 */
public record StaffProfile(MembershipId membershipId, String name, boolean owner, TypeLabel staffType) {

    public StaffProfile {
        if (membershipId == null) {
            throw new IllegalArgumentException("membershipId must not be null");
        }
    }
}

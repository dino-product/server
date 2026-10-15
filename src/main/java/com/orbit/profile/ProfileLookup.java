package com.orbit.profile;

import java.util.Map;
import java.util.Set;

/**
 * 다른 모듈이 계정의 표시 이름을 찾는 공개 계약. 계정은 auth가 발급한 계정 ID로 가리킨다.
 *
 * <p>가입을 마친 계정의 이름만 돌려준다. 가입 미완료 계정과 프로필이 없는 계정은 결과에 키가 없으므로 소비자가 표시 방식을 정한다. 탈퇴한 계정의 표기는 탈퇴 기능과
 * 함께 정한다.
 */
public interface ProfileLookup {

    /** 계정 ID별 이름. 빈 집합이면 빈 결과다. */
    Map<Long, String> findNames(Set<Long> accountIds);
}

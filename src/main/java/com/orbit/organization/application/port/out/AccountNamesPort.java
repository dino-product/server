package com.orbit.organization.application.port.out;

import java.util.Map;
import java.util.Set;

import com.orbit.organization.domain.AccountId;

/** 계정의 표시 이름. 이름은 profile이 소유하므로 organization은 화면에 보일 이름만 빌려 온다. */
public interface AccountNamesPort {

    /** 계정별 이름. 가입을 마치지 않았거나 프로필이 없는 계정은 결과에 없다. */
    Map<AccountId, String> findNames(Set<AccountId> accountIds);
}

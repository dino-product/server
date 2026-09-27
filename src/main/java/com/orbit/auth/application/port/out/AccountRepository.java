package com.orbit.auth.application.port.out;

import java.util.Optional;

import com.orbit.auth.domain.Account;
import com.orbit.auth.domain.AccountId;
import com.orbit.auth.domain.ExternalIdentity;

public interface AccountRepository {

    /**
     * 식별자가 없는 새 계정을 저장하고 식별자를 부여해 돌려준다. 같은 외부 식별의 계정이 이미 있으면 {@link DuplicateIdentityException}을 던지며, 호출자의
     * 트랜잭션과 별개로 즉시 확정하므로 실패해도 호출자는 계속 조회할 수 있다.
     */
    Account saveNew(Account account);

    Optional<Account> findByIdentity(ExternalIdentity identity);

    Optional<Account> findById(AccountId accountId);
}

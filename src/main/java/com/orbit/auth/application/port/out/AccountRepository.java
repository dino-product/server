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

    /** 닫힌 계정도 찾는다. */
    Optional<Account> findById(AccountId accountId);

    /** 같은 계정을 동시에 바꾸는 트랜잭션이 기다리도록 계정을 잠그고 읽는다. 호출자 트랜잭션 안에서만 쓰며 닫힌 계정도 찾는다. */
    Optional<Account> findByIdForUpdate(AccountId accountId);

    /**
     * {@link #findByIdForUpdate}로 잠그고 읽은 계정을 같은 트랜잭션에서 저장한다. 폐쇄 시각을 저장하고 계정에서 끊은 외부 식별을 지우며, 외부 식별 추가는 지원하지
     * 않는다.
     */
    void update(Account account);
}

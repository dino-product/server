package com.orbit.auth.application.port.out;

import java.util.Optional;

import com.orbit.auth.domain.Account;
import com.orbit.auth.domain.ExternalIdentity;

public interface AccountRepository {

    Account save(Account account);

    Optional<Account> findByIdentity(ExternalIdentity identity);
}

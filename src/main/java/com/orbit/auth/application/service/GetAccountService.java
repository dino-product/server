package com.orbit.auth.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.auth.application.error.AuthErrorCode;
import com.orbit.auth.application.port.in.query.GetAccountUseCase;
import com.orbit.auth.application.port.in.query.dto.AccountInfo;
import com.orbit.auth.application.port.in.query.dto.GetAccountQuery;
import com.orbit.auth.application.port.out.AccountRepository;
import com.orbit.auth.domain.Account;
import com.orbit.auth.domain.AccountId;
import com.orbit.shared.error.BusinessException;

/** 토큰이 가리키는 계정이 아직 존재하는지 확인해 돌려준다. 토큰 클레임만으로 응답하지 않는다. */
@Service
public class GetAccountService implements GetAccountUseCase {

    private final AccountRepository accounts;

    public GetAccountService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    @Transactional(readOnly = true)
    public AccountInfo getAccount(GetAccountQuery query) {
        Account account = accounts.findById(new AccountId(query.accountId()))
                .orElseThrow(() -> new BusinessException(AuthErrorCode.ACCOUNT_NOT_FOUND));
        return new AccountInfo(account.id().orElseThrow().value(), account.registeredAt());
    }
}

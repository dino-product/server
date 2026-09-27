package com.orbit.auth.adapter.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.orbit.auth.adapter.in.web.docs.AuthSessionControllerDocs;
import com.orbit.auth.adapter.in.web.security.AuthenticatedAccount;
import com.orbit.auth.application.port.in.command.LogoutUseCase;
import com.orbit.auth.application.port.in.command.dto.LogoutCommand;
import com.orbit.auth.application.port.in.query.GetAccountUseCase;
import com.orbit.auth.application.port.in.query.dto.GetAccountQuery;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthSessionController implements AuthSessionControllerDocs {

    private final GetAccountUseCase getAccountUseCase;
    private final LogoutUseCase logoutUseCase;

    public AuthSessionController(GetAccountUseCase getAccountUseCase, LogoutUseCase logoutUseCase) {
        this.getAccountUseCase = getAccountUseCase;
        this.logoutUseCase = logoutUseCase;
    }

    @Override
    @GetMapping("/me")
    public AuthenticatedAccountResponse me(@AuthenticationPrincipal AuthenticatedAccount account) {
        return AuthenticatedAccountResponse.from(
                getAccountUseCase.getAccount(new GetAccountQuery(account.accountId())), account);
    }

    @Override
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal AuthenticatedAccount account) {
        logoutUseCase.logout(new LogoutCommand(account.tokenId(), account.tokenExpiresAt()));
    }
}

package com.orbit.auth.adapter.in.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.orbit.auth.adapter.in.web.docs.AuthSessionControllerDocs;
import com.orbit.auth.adapter.in.web.security.AuthenticatedAccount;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthSessionController implements AuthSessionControllerDocs {

    @Override
    @GetMapping("/me")
    public AuthenticatedAccountResponse me(@AuthenticationPrincipal AuthenticatedAccount account) {
        return AuthenticatedAccountResponse.from(account);
    }
}

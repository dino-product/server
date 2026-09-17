package com.example.auth.adapter.in.web;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.auth.adapter.in.web.docs.AuthExampleControllerDocs;
import com.example.auth.application.port.in.query.GetAuthSubjectUseCase;
import com.example.auth.application.port.in.query.dto.GetAuthSubjectQuery;

@Validated
@RestController
@RequestMapping("/api/v1/auth/examples")
public class AuthExampleController implements AuthExampleControllerDocs {
    private final GetAuthSubjectUseCase useCase;

    public AuthExampleController(GetAuthSubjectUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    @GetMapping("/subjects/{userId}")
    public AuthSubjectResponse getSubject(@PathVariable Long userId) {
        return AuthSubjectResponse.from(useCase.getSubject(new GetAuthSubjectQuery(userId)));
    }
}

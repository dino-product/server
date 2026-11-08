package com.orbit.profile.adapter.in.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.orbit.profile.adapter.in.web.docs.TermsControllerDocs;
import com.orbit.profile.application.port.in.query.ListCurrentTermsUseCase;

@RestController
class TermsController implements TermsControllerDocs {

    static final String PATH = "/api/v1/terms";

    private final ListCurrentTermsUseCase listCurrentTermsUseCase;

    TermsController(ListCurrentTermsUseCase listCurrentTermsUseCase) {
        this.listCurrentTermsUseCase = listCurrentTermsUseCase;
    }

    @Override
    @GetMapping(PATH)
    public CurrentTermsResponse listCurrentTerms() {
        return CurrentTermsResponse.from(listCurrentTermsUseCase.listCurrentTerms());
    }
}

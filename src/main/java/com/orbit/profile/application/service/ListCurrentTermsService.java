package com.orbit.profile.application.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.orbit.profile.application.port.in.query.ListCurrentTermsUseCase;
import com.orbit.profile.application.port.in.query.dto.TermsDocumentInfo;
import com.orbit.profile.application.port.out.CurrentTermsPort;

/** 설정값만 읽고 DB를 쓰지 않으므로 트랜잭션을 열지 않는다. 계정과 무관해 로그인 전에도 부른다. */
@Service
public class ListCurrentTermsService implements ListCurrentTermsUseCase {

    private final CurrentTermsPort currentTerms;

    public ListCurrentTermsService(CurrentTermsPort currentTerms) {
        this.currentTerms = currentTerms;
    }

    @Override
    public List<TermsDocumentInfo> listCurrentTerms() {
        return currentTerms.currentDocuments().stream()
                .map(document -> new TermsDocumentInfo(document.type(), document.version(), document.url()))
                .toList();
    }
}

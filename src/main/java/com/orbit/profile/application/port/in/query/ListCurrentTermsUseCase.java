package com.orbit.profile.application.port.in.query;

import java.util.List;

import com.orbit.profile.application.port.in.query.dto.TermsDocumentInfo;

public interface ListCurrentTermsUseCase {

    List<TermsDocumentInfo> listCurrentTerms();
}

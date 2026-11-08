package com.orbit.profile.application.port.out;

import java.util.List;

import com.orbit.profile.domain.TermsDocument;
import com.orbit.profile.domain.TermsVersions;

public interface CurrentTermsPort {

    /** 지금 시행 중인 약관 버전. */
    TermsVersions currentVersions();

    /** 지금 시행 중인 약관 문서마다 버전과 본문 주소. {@link com.orbit.profile.domain.TermsDocumentType} 순서다. */
    List<TermsDocument> currentDocuments();
}

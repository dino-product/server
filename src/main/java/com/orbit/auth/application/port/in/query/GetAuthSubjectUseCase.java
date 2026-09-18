package com.orbit.auth.application.port.in.query;

import com.orbit.auth.application.port.in.query.dto.AuthSubjectInfo;
import com.orbit.auth.application.port.in.query.dto.GetAuthSubjectQuery;

public interface GetAuthSubjectUseCase {
    AuthSubjectInfo getSubject(GetAuthSubjectQuery query);
}

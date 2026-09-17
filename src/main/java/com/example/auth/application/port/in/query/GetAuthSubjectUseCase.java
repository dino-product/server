package com.example.auth.application.port.in.query;

import com.example.auth.application.port.in.query.dto.AuthSubjectInfo;
import com.example.auth.application.port.in.query.dto.GetAuthSubjectQuery;

public interface GetAuthSubjectUseCase {
    AuthSubjectInfo getSubject(GetAuthSubjectQuery query);
}

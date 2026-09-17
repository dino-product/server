package com.example.auth.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.auth.application.error.AuthErrorCode;
import com.example.auth.application.port.in.query.GetAuthSubjectUseCase;
import com.example.auth.application.port.in.query.dto.AuthSubjectInfo;
import com.example.auth.application.port.in.query.dto.GetAuthSubjectQuery;
import com.example.auth.application.port.out.LoadAuthSubjectPort;
import com.example.auth.domain.AuthSubject;
import com.example.shared.error.BusinessException;

@Service
public class GetAuthSubjectService implements GetAuthSubjectUseCase {
    private final LoadAuthSubjectPort subjects;

    public GetAuthSubjectService(LoadAuthSubjectPort subjects) {
        this.subjects = subjects;
    }

    @Override
    @Transactional(readOnly = true)
    public AuthSubjectInfo getSubject(GetAuthSubjectQuery query) {
        AuthSubject subject = subjects.findByUserId(query.userId())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.USER_NOT_FOUND));
        return new AuthSubjectInfo(subject.value());
    }
}

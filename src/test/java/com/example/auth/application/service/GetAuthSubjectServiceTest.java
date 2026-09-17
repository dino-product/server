package com.example.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.auth.application.error.AuthErrorCode;
import com.example.auth.application.port.in.query.dto.AuthSubjectInfo;
import com.example.auth.application.port.in.query.dto.GetAuthSubjectQuery;
import com.example.auth.application.port.out.LoadAuthSubjectPort;
import com.example.auth.domain.AuthSubject;
import com.example.shared.error.BusinessException;

@ExtendWith(MockitoExtension.class)
class GetAuthSubjectServiceTest {
    @Mock
    private LoadAuthSubjectPort subjects;

    @Test
    void returnsSubjectFromConsumerModel() {
        when(subjects.findByUserId(1L)).thenReturn(Optional.of(new AuthSubject(1L)));
        assertThat(new GetAuthSubjectService(subjects).getSubject(new GetAuthSubjectQuery(1L)))
                .isEqualTo(new AuthSubjectInfo("user:1"));
    }

    @Test
    void reportsMissingUserWithAuthError() {
        when(subjects.findByUserId(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> new GetAuthSubjectService(subjects).getSubject(new GetAuthSubjectQuery(404L)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(AuthErrorCode.USER_NOT_FOUND));
    }
}

package com.example.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.auth.application.port.in.query.GetAuthSubjectUseCase;
import com.example.auth.application.port.in.query.dto.GetAuthSubjectQuery;
import com.example.support.TestcontainersConfiguration;
import com.example.user.UserLookup;
import com.example.user.UserSummary;

@ApplicationModuleTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class AuthModuleTest {

    @MockitoBean
    private UserLookup userLookup;

    @Autowired
    private GetAuthSubjectUseCase useCase;

    @Test
    void readsSubjectThroughPublishedUserApi() {
        when(userLookup.findById(1L)).thenReturn(Optional.of(new UserSummary(1L, "모듈 사용자")));

        assertThat(useCase.getSubject(new GetAuthSubjectQuery(1L)).subject()).isEqualTo("user:1");
    }
}

package com.example.auth.adapter.out.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.auth.domain.AuthSubject;
import com.example.user.UserLookup;
import com.example.user.UserSummary;

@ExtendWith(MockitoExtension.class)
class UserSubjectAdapterTest {
    @Mock
    private UserLookup users;

    @Test
    void convertsPublicUserResultIntoAuthModel() {
        when(users.findById(1L)).thenReturn(Optional.of(new UserSummary(1L, "홍길동")));
        assertThat(new UserSubjectAdapter(users).findByUserId(1L)).contains(new AuthSubject(1L));
    }

    @Test
    void preservesAbsenceOfUser() {
        when(users.findById(404L)).thenReturn(Optional.empty());
        assertThat(new UserSubjectAdapter(users).findByUserId(404L)).isEmpty();
    }
}

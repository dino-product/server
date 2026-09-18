package com.orbit.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orbit.user.UserSummary;
import com.orbit.user.application.port.out.UserRepository;
import com.orbit.user.domain.User;
import com.orbit.user.domain.UserId;

@ExtendWith(MockitoExtension.class)
class UserLookupServiceTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void findsUserSummary() {
        when(userRepository.findById(new UserId(1L))).thenReturn(Optional.of(User.reconstitute(new UserId(1L), "홍길동")));

        UserLookupService service = new UserLookupService(userRepository);

        assertThat(service.findById(1L)).contains(new UserSummary(1L, "홍길동"));
    }

    @Test
    void returnsEmptyWhenUserDoesNotExist() {
        when(userRepository.findById(new UserId(404L))).thenReturn(Optional.empty());

        UserLookupService service = new UserLookupService(userRepository);

        assertThat(service.findById(404L)).isEmpty();
    }
}

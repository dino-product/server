package com.orbit.auth.application.port.out;

import java.util.Optional;

import com.orbit.auth.domain.AuthSubject;

public interface LoadAuthSubjectPort {
    Optional<AuthSubject> findByUserId(Long userId);
}

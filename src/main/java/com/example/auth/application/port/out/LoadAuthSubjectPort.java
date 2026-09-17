package com.example.auth.application.port.out;

import java.util.Optional;

import com.example.auth.domain.AuthSubject;

public interface LoadAuthSubjectPort {
    Optional<AuthSubject> findByUserId(Long userId);
}

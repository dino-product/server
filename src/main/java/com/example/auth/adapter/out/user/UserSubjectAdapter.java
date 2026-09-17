package com.example.auth.adapter.out.user;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.example.auth.application.port.out.LoadAuthSubjectPort;
import com.example.auth.domain.AuthSubject;
import com.example.user.UserLookup;

@Component
public class UserSubjectAdapter implements LoadAuthSubjectPort {
    private final UserLookup userLookup;

    public UserSubjectAdapter(UserLookup userLookup) {
        this.userLookup = userLookup;
    }

    @Override
    public Optional<AuthSubject> findByUserId(Long userId) {
        return userLookup.findById(userId).map(user -> new AuthSubject(user.id()));
    }
}

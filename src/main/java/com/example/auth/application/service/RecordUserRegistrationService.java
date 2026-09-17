package com.example.auth.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.auth.application.port.in.command.RecordUserRegistrationUseCase;
import com.example.auth.application.port.in.command.dto.RecordUserRegistrationCommand;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class RecordUserRegistrationService implements RecordUserRegistrationUseCase {
    @Override
    @Transactional
    public void record(RecordUserRegistrationCommand command) {
        log.info(
                "Auth example registration received: userId={}, occurredAt={}", command.userId(), command.occurredAt());
    }
}

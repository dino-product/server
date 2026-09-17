package com.example;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.auth.application.service.RecordUserRegistrationService;
import com.example.support.IntegrationTestSupport;
import com.example.user.UserLookup;
import com.example.user.application.port.in.command.RegisterUserUseCase;
import com.example.user.application.port.in.command.dto.RegisterUserCommand;
import com.example.user.application.port.in.command.dto.RegisteredUserInfo;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

class UserRegistrationEventIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private RegisterUserUseCase registerUser;

    @Autowired
    private UserLookup userLookup;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final AuthLogAppender appender = new AuthLogAppender();
    private final Logger authLogger = (Logger) LoggerFactory.getLogger(RecordUserRegistrationService.class);

    @BeforeEach
    void captureAuthLogs() {
        appender.start();
        authLogger.addAppender(appender);
    }

    @AfterEach
    void stopCapturingAuthLogs() {
        authLogger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void recordsRegistrationAfterCommitOnAnotherThread() throws InterruptedException {
        long publishingThreadId = Thread.currentThread().threadId();
        RegisteredUserInfo user = new TransactionTemplate(transactionManager).execute(status -> {
            RegisteredUserInfo registered = registerUser.register(new RegisterUserCommand("커밋 사용자"));
            assertThat(appender.events).as("커밋 전에는 후속 처리를 기록하지 않는다").isEmpty();
            return registered;
        });

        AuthLog auth = appender.events.poll(5, TimeUnit.SECONDS);

        assertThat(user).isNotNull();
        assertThat(auth).as("커밋한 사용자 등록의 후속 처리가 기록된다").isNotNull();
        assertThat(auth.userId()).isEqualTo(user.id());
        assertThat(auth.threadId()).isNotEqualTo(publishingThreadId);
        assertThat(auth.transactionActive()).isTrue();
        assertThat(userLookup.findById(user.id())).isPresent();
    }

    @Test
    void doesNotRecordRolledBackRegistration() throws InterruptedException {
        RegisteredUserInfo user = new TransactionTemplate(transactionManager).execute(status -> {
            RegisteredUserInfo registered = registerUser.register(new RegisterUserCommand("롤백 사용자"));
            status.setRollbackOnly();
            return registered;
        });

        assertThat(user).isNotNull();
        assertThat(userLookup.findById(user.id())).isEmpty();
        assertThat(appender.events.poll(500, TimeUnit.MILLISECONDS))
                .as("롤백한 사용자 등록은 후속 처리를 기록하지 않는다")
                .isNull();
    }

    private record AuthLog(Long userId, long threadId, boolean transactionActive) {}

    private static class AuthLogAppender extends AppenderBase<ILoggingEvent> {

        private final BlockingQueue<AuthLog> events = new LinkedBlockingQueue<>();

        @Override
        protected void append(ILoggingEvent event) {
            if (event.getMessage().startsWith("Auth example registration received:")) {
                events.add(new AuthLog(
                        (Long) event.getArgumentArray()[0],
                        Thread.currentThread().threadId(),
                        TransactionSynchronizationManager.isActualTransactionActive()));
            }
        }
    }
}

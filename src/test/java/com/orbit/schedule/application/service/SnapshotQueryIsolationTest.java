package com.orbit.schedule.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttribute;

@DisplayName("여러 번 읽어 한 화면을 만드는 조회의 트랜잭션")
class SnapshotQueryIsolationTest {

    // WorkQueryPort를 두 번 이상 부르거나 다른 조회와 함께 한 화면을 만드는 서비스는 여기에 추가한다.
    @ParameterizedTest
    @ValueSource(
            classes = {
                GetTimetableService.class,
                SearchWorksService.class,
                GetProgressBoardService.class,
                GetWorkDetailService.class,
                ExportWorksService.class,
                GetBacklogSummaryService.class
            })
    @DisplayName("한 스냅샷(REPEATABLE READ) 읽기 전용 트랜잭션에서 실행한다")
    void runsInRepeatableReadSnapshot(Class<?> service) {
        // 클래스 수준·합성 애너테이션까지 Spring이 실행 시 적용하는 규칙 그대로 판정한다.
        AnnotationTransactionAttributeSource attributes = new AnnotationTransactionAttributeSource();
        List<Method> publicMethods = Arrays.stream(service.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()) && !method.isSynthetic())
                .toList();

        assertThat(publicMethods).isNotEmpty().allSatisfy(method -> {
            TransactionAttribute attribute = attributes.getTransactionAttribute(method, service);
            assertThat(attribute).as(method.toString()).isNotNull();
            assertThat(attribute.isReadOnly()).as(method.toString()).isTrue();
            assertThat(attribute.getIsolationLevel())
                    .as(method.toString())
                    .isEqualTo(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        });
    }
}

package com.orbit.schedule.application.port.out;

import java.util.Optional;

import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;

/**
 * Work 애그리게잇의 저장·조회 출력 포트.
 *
 * <ul>
 *   <li>조직 격리는 서비스가 판단한다. 조회한 작업이 요청자의 조직과 다르면 존재 여부가 드러나지 않도록 WORK_NOT_FOUND로 처리한다.
 *   <li>{@link #save}는 새 작업이면 식별자를 채워 돌려주고, 기존 작업이면 같은 트랜잭션에서 {@link #findById}로 읽은 작업의 변경을 반영한다. 구현은 조회 시점의
 *       버전으로 동시 변경을 검출한다.
 * </ul>
 */
public interface WorkRepository {

    Work save(Work work);

    Optional<Work> findById(WorkId workId);
}

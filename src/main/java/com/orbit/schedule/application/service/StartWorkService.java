package com.orbit.schedule.application.service;

import org.springframework.stereotype.Service;

import com.orbit.schedule.application.port.in.command.StartWorkUseCase;
import com.orbit.schedule.application.port.in.command.dto.StartWorkCommand;

/**
 * 작업 시작. 담당 기사 본인이 화면에서 본 최신 배정의 수락된 작업을 작업중으로 바꾸고 지금 시각을 시작 시각으로 남긴다. 배정이 그대로인 채 이미 시작한 작업(완료
 * 포함)에 다시 보내면(중복 전송) 아무것도 바꾸지 않고 성공한다. 그 배정 일정의 시작 가능 시간({@link WorkSchedule#isTooEarlyToStart}·{@link
 * WorkSchedule#isTooLateToStart})을 벗어나면 이를 때는 409(SCHEDULE-017), 늦을 때는 409(SCHEDULE-018)다. 같은 기사에게 작업중인 다른 작업이
 * 있어도 시작할 수 있어 기사의 다른 작업은 읽지 않는다.
 *
 * <p>작업 식별자 형식을 확인한 뒤 작업을 읽기 전에 기사를 잠근다(대기 한도 초과 409 SCHEDULE-006). 같은 기사를 잠그는 대기함 반환(역할
 * 변경·비활성화·탈퇴)·같은 작업의 중복 시작과 한 줄로 서서 뒤에 온 쪽이 앞선 커밋을 보고 판정한다(반환된 작업이면 404, 이미 시작했으면 중복 전송 성공).
 * 관리자의 일정 변경도 같은 기사를 잠가 한 줄로 서므로, 시작이 늦으면 배정이 바뀌었다는 409(SCHEDULE-010), 일정 변경이 늦으면 409(SCHEDULE-016)다.
 * 재배정(새 담당기사를 잠금)·배정 해제·취소·기본정보 수정(잠그지 않음)과의 경합은 작업 버전 검사로 뒤 저장이 실패한다. 관리자 쪽 재배정·해제가
 * 늦으면 409(SCHEDULE-016)이고, 시작이 늦으면 새 트랜잭션에서 한 번 다시 실행해 다시 읽은 상태로 판정한다
 * ({@link WorkVersions#retryOnConflict}). 다시 실행해도 충돌하면 409(SCHEDULE-016)다. 오류 확인 순서는 schedule 지침의 기사 유즈케이스 순서를 따른다.
 */
@Service
public class StartWorkService implements StartWorkUseCase {

    private final StartWorkTransaction transaction;

    public StartWorkService(StartWorkTransaction transaction) {
        this.transaction = transaction;
    }

    @Override
    public void start(StartWorkCommand command) {
        WorkVersions.retryOnConflict(() -> transaction.start(command));
    }
}

# Auth 모듈

## 책임과 범위

프로젝트 컨벤션을 보여주는 예제입니다. user의 공개 조회·이벤트를 auth 소유 값으로 변환하여 모듈 경계, Command/Query 계약, Domain/Application/Adapter 분리를 보여줍니다.

실제 로그인, 비밀번호 검증, JWT·세션 발급, 인증 상태 설정은 구현하지 않습니다. 예제 subject는 인증 증명이 아니며 접근 권한을 부여하지 않습니다. auth는 사용자 원본이나 별도 테이블을 소유하지 않습니다.

## 조회 흐름

```text
GET /api/v1/auth/examples/subjects/{userId}
  → GetAuthSubjectQuery → GetAuthSubjectUseCase
  → GetAuthSubjectService → LoadAuthSubjectPort
  → UserSubjectAdapter → user.UserLookup
  → UserSummary의 id를 AuthSubject로 변환
  → AuthSubjectInfo → AuthSubjectResponse
```

- 양수 식별자에 해당하는 사용자가 있으면 `subject: "user:{id}"`를 반환합니다. `UserSubjectAdapter`는 `UserSummary`에서 식별자만 선택하여 auth Domain/Application에 전달합니다.
- `AuthSubject`는 양수 식별자와 `user:` subject 이름 규칙을 소유합니다. user의 Domain·DTO나 Spring/JPA에 의존하지 않습니다.
- 없는 사용자는 `AuthErrorCode.USER_NOT_FOUND` (`AUTH-001`, HTTP 404), 양수가 아닌 경로 값은 공통 입력 오류 (`COMMON-400`, HTTP 400)를 반환합니다.
- 공개 예제 경로는 `/api/v1/auth/examples/**`입니다. `/api/v1/auth/**` 전체를 공개하지 않습니다.

## 이벤트 흐름

```text
user.UserRegistered
  → UserRegisteredListener
  → RecordUserRegistrationCommand
  → RecordUserRegistrationUseCase
  → RecordUserRegistrationService
```

- 리스너가 공개 이벤트를 auth 소유 Command로 변환합니다. Application과 Domain은 user 타입을 참조하지 않습니다.
- 등록 커밋 후 별도 스레드·트랜잭션에서 `userId`, `occurredAt`만 로그로 기록합니다. 롤백 시 소비하지 않습니다.
- 발생 시각은 user의 발행 시각이며 커밋 시각이 아닙니다. 소비 실패가 이미 커밋한 등록을 되돌리지 않습니다.
- 로그 외 영속 모델·자동 재처리는 없습니다. 전달 보장은 [트랜잭션 이벤트](../conventions/persistence/events.md#events)를 따릅니다.

## 패키지와 공개 계약

- 조회 계약·값: `application/port/in/query`, `query/dto`.
- 이벤트 후속 처리 계약·값: `application/port/in/command`, `command/dto`.
- 구현 두 개는 `application/service`에 둡니다. 아직 service 하위 command/query를 나누지 않습니다.
- 출력 Port는 `application/port/out`, user 연동 구현은 `adapter/out/user`에 둡니다.
- 현재 다른 모듈에 공개하는 타입은 없습니다. 허용 의존성은 [도메인 지도](README.md)가 관리합니다.

소비 모듈 모델의 일반 기준은 [아키텍처](../conventions/architecture/external-models.md#external-models), 예제의 선택 배경은 [ADR-001](../adr/001-backend-architecture.md#예제-모듈)을 따릅니다.

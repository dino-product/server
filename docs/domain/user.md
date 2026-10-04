# User 모듈

## 책임

- 사용자 표시 이름의 불변식을 보장합니다.
- 사용자를 PostgreSQL에 등록합니다.
- 다른 모듈에 최소 사용자 요약 조회를 제공합니다.
- 등록 트랜잭션에서 `UserRegistered` 이벤트를 발행합니다.

## 공개 계약

- `UserLookup.findById(Long)`: 사용자의 `id`, `displayName` 요약 조회
- `UserSummary`: 조회 결과 값
- `UserRegistered`: `userId`, UTC `occurredAt`을 담은 완료 이벤트

`occurredAt`은 트랜잭션 안에서 이벤트를 발행한 시각입니다. 커밋 시각이나 후속 처리 완료 시각을 의미하지 않습니다.

## 내부 흐름

```text
POST /api/v1/users
  → RegisterUserUseCase
  → User.register
  → UserRepository port
  → JPA adapter
  → UserRegistered 발행
```

표시 이름은 공백일 수 없고 100자 이하여야 합니다. 공개 이벤트에는 표시 이름을 넣지 않습니다.

## 패키지 예제

- `application/port/in/command/RegisterUserUseCase`: 등록 입력 계약.
- `application/port/in/command/dto`: `RegisterUserCommand`, 등록 결과 `RegisteredUserInfo`.
- `application/service`: 등록과 공개 요약 조회 구현. 소규모이므로 service 하위 command/query는 만들지 않습니다.
- 공개 조회 `UserLookup`과 전달 값 `UserSummary`, 이벤트 `UserRegistered`는 루트에 둡니다.
- user는 auth에 의존하지 않습니다.

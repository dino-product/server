<a id="organization"></a>
# 조직 도메인

`com.orbit.organization.domain`은 프레임워크와 다른 모듈에 의존하지 않는다. 루트 공개 계약은 없고 모듈의 허용 의존성은 없다. 전체 모듈 상태는 [도메인 지도](README.md#모듈별-책임과-공개-계약)를 따른다.

## 용어

인증 경계에서 전달하는 식별자는 `accountId`, 조직이 보유하는 외부 계정 참조는 `authAccountId`다. `AuthAccountId(Long)`는 조직 자체 값객체이며 양수만 허용한다. 직원 소속은 `Membership`, 회사별 기사 계약은 `Technician`, 참여 요청은 `ParticipantRequest`(`participant_request`)다. DB 이름은 후속 영속성 작업의 매핑 기준이며 이 모듈에는 JPA 매핑이 없다.

## 참여 요청

`ParticipantRequest`는 양수 요청 ID·조직 ID·외부 계정 ID, 희망 유형(`STAFF`/`TECHNICIAN`), 경로(`CODE`/`LINK`/`QR`), 요청 시각으로 생성하며 처음 상태는 `PENDING`이다. 시각은 호출자가 UTC `Instant`로 전달한다.

- 승인: `APPROVED`로 바꾸고 필수 확정 유형·처리 시각을 기록한다. 희망 유형은 보존하며 다른 유형으로 확정할 수 있다.
- 거절: `REJECTED`로 바꾸고 선택 사유·처리 시각을 기록한다. null·공백 사유는 미입력으로 취급한다.
- 취소: `CANCELLED`로 바꾸고 처리 시각을 기록한다.
- 세 처리는 `PENDING`에서만 허용한다. 처리 시각은 요청 시각보다 앞설 수 없다. 거부된 변경은 기존 상태를 보존한다.
- 불변식 위반은 `OrganizationRuleViolation`으로 전달하며 HTTP·공통 오류 타입에 의존하지 않는다.

## 후속 계층 책임

현재 회사·소속·계약·유형 모델과 Application·Adapter는 구현되지 않았다. 참여 요청 모델 자체는 계정·회사 존재, 현재 회사 코드, 행위자 권한, 기존 관계·대기 요청 중복을 조회하지 않는다. 후속 Application은 승인 권한과 중복을 확인하고 승인·직원 소속 또는 기사 계약 생성을 한 트랜잭션에서 처리해야 한다. 저장소는 동시 요청의 중복·상태 충돌도 막아야 한다. 취소는 요청자 본인만 할 수 있으며 이 검증도 인증 경계와 연결하는 Application의 책임이다.

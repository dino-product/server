<a id="organization-domain"></a>
# ADR-003: 조직 도메인의 소속·계약과 총관리자 모델

- 상태: Accepted
- 기준일: 2026-09-28
- 관련 작업: [HM-258](https://humanistired.atlassian.net/browse/HM-258)
- 관련 결정: [ADR-001](001-backend-architecture.md#배포와-모듈-경계)

## 배경

기존 기획 초안은 기사를 직원 소속의 역할로 표현하고 역할 전환을 허용했다. HM-258은 직원 소속과 회사별 기사 계약을 구분하며, 같은 회사에서는 비활성을 포함해 한 계정이 둘 중 하나만 가지도록 확정했다. 관리자와 총관리자는 같은 개념이므로 직원 `role`과 회사 소유자 필드를 함께 두면 권한 원본이 중복된다.

## 결정

- 서버 회사 명칭은 `Organization`을 유지한다. 직원 소속은 `Membership`, 회사별 기사 계약은 `Technician`, 참여 요청은 `ParticipantRequest`이며 후속 DB 매핑 이름은 `participant_request`다.
- 인증 경계의 식별자는 `accountId`, 조직 내부 외부 계정 참조는 자체 `AuthAccountId(Long)` / `authAccountId`다. 다른 모듈의 내부 타입을 가져오지 않는다.
- `Membership`과 `Technician`, `StaffType`과 `TechnicianType`은 각각 별도 모델·ID로 둔다. 유형은 선택이며 최소 활성 유형 개수 제한은 없다. 값의 규칙이 같은 이름·색상 값객체만 공유한다.
- 총관리자는 회사의 필수 `ownerMembershipId` 하나로 표현한다. 직원 `role`은 두지 않는다. 같은 회사의 활성 직원만 총관리자가 될 수 있고 위임 전에 비활성화할 수 없다.
- 조직 도메인은 Spring·JPA·Application·Adapter에 의존하지 않는다. 개별 모델은 자체 불변식을, 순수 `ParticipationPolicy`는 전달받은 현재 관계에 대한 중복 판정을 소유한다.
- 기사 참여 시각은 계약 생성 시각 `contractedAt`으로 사용한다. 별도 참여일은 두지 않는다. `last_active_at`의 활동 의미·갱신 주체는 후속 작업에서 합의한다.

## 영향과 제약

직원↔기사 전환과 기사 유형의 직원 교차 지정은 제공하지 않는다. 승인과 관계 생성, 회사 생성과 최초 총관리자 소속 생성은 후속 Application에서 원자적으로 처리해야 한다. 조직·계정별 관계 조회는 비활성을 제외하지 않으며 동시 생성·승인 및 위임·비활성화 충돌을 저장소가 제어해야 한다. 메모리 정책 검사만으로 DB 전체의 중복을 보장하지 않는다.

업종은 확정된 6종을 사용한다. 색상은 사용자 요청에 따라 임시 번호 1~8로 두고 실제 색상 매핑을 TODO로 남긴다. 회사 코드는 8자 대문자 Crockford Base32(`0-9A-HJKMNP-TV-Z`, I·L·O·U 제외) 형식을 검증한다. 발급은 순수 무작위이며 중복 시 재발급하고 DB unique 제약으로 충돌을 최종 방어한다. 발급 구현과 코드 변경·폐기 정책은 후속 계층의 책임이다.

## 검증

Spring Context 없는 조직 도메인 테스트에서 이름 경계·상태 전이·유형 소유권·총관리자 위임과 비활성화·비활성 포함 중복을 확인한다. `ArchitectureTest`와 `ModularityTest`가 내부 계층 및 모듈 의존성을 검사한다. 상세 동작의 원본은 [조직 도메인](../domain/organization.md#organization)이다.

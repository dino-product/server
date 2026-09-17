<a id="profiles"></a>
# 프로필과 실행 환경

- 패키징된 설정은 기본 활성 프로필을 선택하지 않습니다. 환경의 `SPRING_PROFILES_ACTIVE`로 지정합니다.
- 공통·`prod`의 `ddl-auto`는 `none`, `create-drop`은 로컬 예제·테스트에서만 허용합니다. 로컬 DB에 보존할 데이터를 넣지 않습니다.
- 운영은 `SPRING_PROFILES_ACTIVE=prod`로 실행합니다. `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`는 기본값 없이 받아 누락 시 시작에 실패하게 합니다.
- `prod,local` 동시 지정에도 로컬 설정이 활성화되지 않도록 프로필 표현식으로 보호합니다. 운영 OpenAPI UI는 비활성화합니다.
- 설정은 `application-{profile}.yml`로 분리하고 공통 설정을 중복하지 않습니다.
- 현재 마이그레이션 도구는 없습니다. 운영 배포 전에 마이그레이션 전략을 ADR로 결정합니다.

실행 명령: [빠른 시작](../../../README.md#빠른-시작). 참고: [영속성 결정](../../adr/001-backend-architecture.md#영속성과-이벤트).

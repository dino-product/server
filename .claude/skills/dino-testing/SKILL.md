---
name: dino-testing
description: 테스트를 작성·수정할 때, 기능·버그 구현 전에 재현(실패) 테스트를 만들 때, 집중 검사나 전체 검증(gradle test·spotless·checkstyle·bootJar)을 실행하고 결과를 보고할 때 사용합니다. 검사 선택표, Testcontainers DB 검사 승인, 완료 기준을 적용합니다.
---

# 테스트와 검증

테스트 경로의 `src/test/java/com/orbit/AGENTS.md`와 대상 소스 모듈의 `AGENTS.md`를 함께 적용합니다. 원본: [설계](references/design.md), [검사 선택·완료·근거](references/verification.md), [DB 승인](references/database-approvals.md).

## 순서

1. **재현 테스트 먼저**: 기능·버그는 기대 행동의 테스트를 먼저 쓰고 의도한 이유로 실패하는지 확인한 뒤 구현합니다. 정상·거부·경계 조건을 노션 명세의 검증 정책·수용 기준에서 뽑습니다.
2. **테스트 종류**:

   | 대상 | 방식 |
   | --- | --- |
   | Domain/Application 규칙 | Spring Context 없는 단위 테스트, fake는 `src/test`에 두고 스캔 애너테이션 없음 |
   | 모듈 조립 | `@ApplicationModuleTest` |
   | DB·API·이벤트 | PostgreSQL Testcontainers, 클래스 수준 `@Transactional` 금지 |

   시간은 고정 `Clock`/`Instant`로 다룹니다. 테스트 이름은 영문 camelCase 행동 서술입니다(예: `hidesWorkOfOtherOrganization`).
3. **집중 검사**: [선택표](references/verification.md#selection)와 모듈 `AGENTS.md`의 집중 검증에서 정확한 클래스를 고릅니다.
   - `./gradlew test --tests '<클래스>'`
   - 같은 단위의 클래스는 `--tests`를 여러 번 붙여 한 번에 실행합니다.
4. **완료 검증**: 각 PR의 최종 상태에서 실행합니다. `--tests` 필터는 쓰지 않습니다.
   ```bash
   ./gradlew spotlessApply
   ./gradlew spotlessCheck checkstyleMain checkstyleTest
   ./gradlew compileJava compileTestJava
   ./gradlew test -PrequireAllTests=true
   ./gradlew bootJar
   ```

## 승인과 보고

- `./gradlew test`·`build`·`check`는 Testcontainers로 테스트 DB를 만들고 지우므로 [DB 승인](references/database-approvals.md#database) 대상입니다. 팀 설정에서 확인(ask) 대상으로 걸려 있습니다. 실행 전에 명령·대상 테스트·정리 범위를 요약합니다.
- Docker가 없어 건너뛴 테스트가 있으면 전체 통과가 아닙니다. 성공·실패·건너뛰기·필터 여부를 구분해 보고합니다.
- assertion·아키텍처 규칙·CI 검사를 약화해 통과시키지 않습니다. 실패는 제품·테스트·환경 문제로 구분합니다.

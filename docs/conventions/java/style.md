<a id="style"></a>
# Java 포맷과 명명

- Java 레이아웃은 Spotless의 Palantir Java Format을 따릅니다. 버전 원본은 `gradle/libs.versions.toml`입니다.
- 공백 4칸·줄 길이 120자를 기준으로 표현식·annotation·체인·빈 본문 배치는 formatter에 맡깁니다. `formatAnnotations()`로 annotation 줄바꿈을 덮어쓰지 않습니다.
- import 순서는 static, `java`, `javax`, `jakarta`, `org`, `com`, 기타입니다. 미사용 import는 formatter가 제거하며 wildcard import는 금지합니다.
- UTF-8·LF, Gradle 공백 4칸, YAML 2칸을 사용합니다. YAML 검사는 후행 공백·탭 변환·마지막 개행만 처리하며 구조를 재정렬하지 않습니다.
- 작성 후 `./gradlew spotlessApply`, `./gradlew spotlessCheck checkstyleMain checkstyleTest`를 순서대로 실행합니다. CI는 수정 없이 검사합니다.
- Checkstyle의 줄 길이·명명·코드 규칙을 지킵니다. 검사 전체 비활성화와 formatter 결과 수동 복원은 금지합니다. 제외 대상은 `build/generated/querydsl`뿐이며 직접 작성한 `Q*` 클래스도 검사합니다.
- IDE도 Gradle Spotless 설정을 사용하며 저장 시 기본 formatter로 결과를 바꾸지 않습니다.
- `get...`: 없으면 예외, `find...`: `Optional<T>`, `list...`: null이 아닌 목록, `exists...`: 존재 여부, `from`: 단일 원본 객체에서 생성.
- 최상위 Web 입력은 `Request`, Application 입력은 `Command`/`Query`, 결과는 `Info`, Web 출력은 `Response` 접미사를 사용합니다.
- 중첩 부분 모델은 `Profile`처럼 문맥에 맞는 역할명을 사용합니다. DTO 변경 시 [소유·재사용 규칙](../web/dto.md#dto)을 적용합니다.

참고: [포맷 선택 배경](../../adr/001-backend-architecture.md#포맷과-검증).

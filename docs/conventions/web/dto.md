<a id="dto"></a>
# Web DTO 구성

- Request/Response는 Java `record`를 우선합니다. `{module}.adapter.in.web`에 두고 타입이 적으면 평면, 독립 유스케이스가 늘면 `registration`, `profile`, `search` 등 함께 변경되는 기능별 하위 패키지로 묶습니다.
- 모듈 루트의 `dto`, `request`, `response` 기술 유형 패키지와 `UserDtos` 같은 독립 DTO 모음 타입은 금지합니다.
- 독립적으로 이름 붙일 수 있는 Request/Response는 파일당 최상위 `record` 하나로 선언합니다.
- 중첩 `record`는 상위 Request/Response 전용이며 밖에서 독립 의미가 없을 때만 사용합니다.
- 여러 API의 의미·노출 정책·변경 주기가 같으면 가장 가까운 공통 기능 패키지의 최상위 `record`로 추출합니다. 필드만 같고 의미·노출 정책이 다르면 별도 타입을 유지합니다.
- Web DTO는 모듈 간 공유하지 않습니다. 다른 모듈의 루트/named interface 계약을 받아 자신의 DTO로 변환합니다.

한 응답 전용 부분 모델:

```java
public record UserDetailResponse(Long id, Profile profile) {

    public record Profile(String name, String profileImageUrl) {}
}
```

여러 API가 같은 간이 프로필을 공유한다면 별도 파일의 `UserProfileSummaryResponse(String name, String profileImageUrl)` record로 추출합니다.

<a id="shared"></a>
# Shared 공개 계약

- 오류는 `shared::error`, OpenAPI는 `shared::openapi`로 공개합니다. 공개 타입·소비 모듈 원본은 [도메인 지도](../../domain/README.md#모듈별-책임과-공개-계약)입니다.
- 설정·공통 응답·예외 처리·보안 구현은 `src/main/java/com/orbit/shared/internal`에 두고 공개하지 않습니다.
- 여러 모듈이 실제 재사용하는 작고 안정적인 계약만 `shared`에 둡니다. 한 모듈 전용·사용처 없는 타입은 이동하지 않습니다.
- 페이지 요청·응답은 소유 모듈의 Web Adapter에 먼저 둡니다. 둘 이상 모듈의 의미·노출 정책·변경 주기가 같아질 때만 기술 중립적인 공통 계약을 설계합니다.

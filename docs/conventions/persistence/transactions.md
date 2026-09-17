<a id="transactions"></a>
# 트랜잭션과 시간

- 변경 UseCase는 `@Transactional`, 조회 UseCase는 `@Transactional(readOnly = true)`를 적용합니다.
- 날짜·시각은 UTC `Instant`, 테스트 가능한 시간은 주입받은 `Clock`을 사용합니다.

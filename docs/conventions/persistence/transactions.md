<a id="transactions"></a>
# 트랜잭션과 시간

- 변경 UseCase는 `@Transactional`, 조회 UseCase는 `@Transactional(readOnly = true)`를 적용합니다.
- 날짜·시각은 UTC `Instant`, 테스트 가능한 시간은 주입받은 `Clock`을 사용합니다. 주입되는 `Clock`은 저장소(PostgreSQL) 정밀도인 마이크로초 단위로 끊으므로, 시각 컬럼도 마이크로초 정밀도로 매핑합니다.

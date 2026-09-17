<a id="jpa"></a>
# JPA 모델과 생성물

- JPA Entity는 Domain 모델과 분리하고 Persistence Adapter에서 변환합니다. 기본 생성자는 `protected`, 연관관계는 지연 로딩이 기본입니다.
- Entity에 공개 `@Setter`를 두지 않고 의미 있는 도메인 메서드로 상태를 변경합니다.
- QueryDSL 생성물은 `build/generated/querydsl`에 두며 직접 수정하지 않습니다.

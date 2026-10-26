-- JPA 매핑으로 표현할 수 없는 부분 유일 인덱스. Hibernate가 스키마를 자동 생성한 직후(local·test의 create-drop) 실행한다.
-- 운영은 스키마를 자동 생성하지 않으므로 운영 DDL에도 같은 인덱스를 둔다. Hibernate가 한 줄을 한 문장으로 읽으므로 문장마다 한 줄로 쓴다.
-- 직원 유형·기사 유형: 같은 발주사에서 삭제되지 않은 유형끼리만 이름이 유일하다([조직·계정] 정책 §7, 소프트 삭제).
create unique index uk_company_membership_category_undeleted_name on company_membership_category (company_id, name) where is_deleted = false;
create unique index uk_company_technician_category_undeleted_name on company_technician_category (company_id, name) where is_deleted = false;
-- 작업 유형: 같은 발주사에서 삭제되지 않은 작업 유형끼리만 이름이 유일하다([조직·계정] 정책 §7, 소프트 삭제).
create unique index uk_work_type_undeleted_name on work_type (company_id, name) where is_deleted = false;

-- schedule: 현장 기본주소에서 구한 좌표(WGS84, 소수점 6자리). 길찾기용 부가 정보라 비어 있을 수 있고, 위도·경도는 함께 있거나 함께 비어 있다(HM-370).
alter table work add column customer_latitude numeric(8, 6);
alter table work add column customer_longitude numeric(9, 6);
alter table work add constraint ck_work_customer_coordinates
    check ((customer_latitude is null) = (customer_longitude is null));

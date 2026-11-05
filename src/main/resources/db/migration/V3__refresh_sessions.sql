-- auth: 기기 세션(refresh token). 토큰 원문 대신 비밀값 SHA-256 해시만 둔다(HM-337).
create table refresh_sessions (
    id varchar(36) not null,
    account_id bigint not null,
    token_hash varchar(64) not null,
    previous_token_hash varchar(64),
    rotated_at timestamp(6) with time zone,
    started_at timestamp(6) with time zone not null,
    expires_at timestamp(6) with time zone not null,
    constraint pk_refresh_sessions primary key (id),
    constraint fk_refresh_sessions_account foreign key (account_id) references accounts (id)
);

create index ix_refresh_sessions_account_id on refresh_sessions (account_id);

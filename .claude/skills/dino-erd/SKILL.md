---
name: dino-erd
description: ERDCloud의 팀 ERD(Main ERD)를 조회하거나 테이블·컬럼·관계·메모를 바꿀 때 사용합니다. 기획 버전 동기화·설계 결정을 ERD에 반영하거나, 작업 티켓에 ERD 갱신이 포함될 때 해당합니다.
---

# ERD 변경

원본: [ERDCloud 변경](references/erdcloud.md) — 범위, 접근, MCP 동작 특성, 확인 방법.

## 절차

1. **접근 확인**: ERDCloud MCP 도구가 있는지 봅니다. 없으면 [접근](references/erdcloud.md#access)대로 사용자가 등록하고 세션을 다시 시작하도록 안내합니다.
2. **기준선**: `list_tables`·`list_memos`·`list_domains`로 현재 상태를 읽고 SQL Preview를 저장합니다. 메모의 설계 결정과 [범위](references/erdcloud.md#scope)로 손댈 테이블을 정합니다.
3. **합의**: 현재 → 변경 → 근거(Notion 절·설계 결정)를 테이블별 표로 보여 줍니다. 범위 밖 테이블, 메모와 다른 변경, [MCP 동작](references/erdcloud.md#mcp-behavior) 때문에 생길 부수 효과(컬럼 위치 이동 등)를 함께 적고 [게시 승인](../dino-pr/references/publishing-approvals.md#publishing)을 받습니다.
4. **반영**: 스냅샷을 만든 뒤 컬럼 수정은 `update_columns`로 묶고, 추가·관계·메모 순으로 반영합니다. 결과가 예상과 다르면 멈추고 알립니다.
5. **확인**: [확인](references/erdcloud.md#verify)대로 SQL Preview diff를 보고, 변경 내용·부수 효과·메모로만 남긴 제약을 보고합니다. 설계 결정이 바뀌었으면 BC 문서도 같은 작업에서 고칩니다.

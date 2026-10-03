---
name: dino-review
description: PR을 올리기 전 로컬에서 브랜치·diff를 리뷰할 때, "리뷰해줘"·"PR 전에 검토" 요청이나 dino-pr이 PR 초안을 마무리할 때 사용합니다. 노션 명세와 코드 흐름(비즈니스), 컨벤션·모듈 계약·결함(기술)을 함께 보고 임시 HTML 보고서를 만든 뒤 PR 본문 검증 절에 리뷰 마커를 남깁니다.
---

# 로컬 리뷰

원본: [리뷰 절차](references/procedure.md), [보고서와 마커](references/report.md). CI는 리뷰를 실행하지 않고 PR 본문의 마커만 확인합니다. 리뷰 요청만으로 코드를 고치거나 PR·이슈에 게시하지 않습니다.

## 순서

1. **범위**: base/head SHA, 커밋 순서, PR 종류를 확인하고 `.claude/pr-drafts/`의 초안과 계획 파일을 읽습니다. 커밋하지 않은 변경이 있으면 먼저 알립니다.
2. **비즈니스**: 초안 `## 기능 명세`의 노션 절을 하위 에이전트로 읽어 규칙 목록을 받고, 규칙마다 실제 코드 흐름과 근거 테스트를 추적합니다. 노션에 없는 사용자 체감 동작은 `명세 없음`으로 적습니다. 명세가 없는 PR 종류면 건너뛴 이유를 적습니다([비즈니스 관점](references/procedure.md#비즈니스-관점)).
3. **기술**: 다이어그램·구현 내용 표와 코드의 일치를 먼저 보고, 모듈 계약·결함·컨벤션·PR 단위·문서를 봅니다. 자동 검사가 판정한 항목은 다시 지적하지 않습니다([기술 관점](references/procedure.md#기술-관점)).
4. **보고서**: `.claude/reviews/<브랜치의 / 를 - 로>-<head7>.json`에 [결과](references/report.md#result)를 쓰고 `python3 .claude/skills/dino-review/scripts/review_report.py render <결과.json>`으로 HTML을 만듭니다. 경로와 `수정 필요`·`판단 필요` 건수, 가장 중요한 지적만 대화에 요약합니다.
5. **마커**: 리뷰를 마치면 지적이 남아도 `review_report.py marker <결과.json> --draft <초안>`으로 초안 `## 검증` 절에 [마커](references/report.md#marker)를 남깁니다. 이미 게시한 PR이면 본문 갱신을 제안하고 사용자가 요청하면 갱신합니다. 이후 커밋을 더하면 다시 리뷰합니다.

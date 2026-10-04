---
name: dino-commit
description: git 커밋을 만들 때(스테이징 포함), 작업 브랜치를 만들 때, 변경을 어떤 커밋 단위로 나눌지 정할 때 사용합니다. 커밋 제목 형식 type(scope): 한국어 명사형 요약, 단위별 검사 후 커밋, Git 승인 대상을 적용합니다.
---

# 브랜치와 커밋

원본: [커밋 계약](references/commits.md), [유형](references/change-types.md), [Git 승인](references/git-approvals.md). 제목 형식은 PreToolUse hook(`.claude/hooks/check_commit_message.py`)이 커밋 직전에 검사합니다.

## 규칙

- **브랜치**: 기본 브랜치(`develop`)에서 직접 커밋하지 않습니다. `<HM-키>/<짧은-작업명>`을 쓰고, 팀·사용자가 지정한 이름이나 기존 작업 브랜치를 우선합니다.
- **단위**: 한 행동의 구현, 필수 테스트, 계약 문서를 한 커밋에 넣습니다. 파일·계층·구현/테스트별로 나누지 않습니다. 관련 집중 검사를 통과한 직후 커밋하고 다음 단위로 넘어갑니다. 실패하거나 검사를 실행하지 않은 단위는 커밋하지 않습니다.
- **스테이징**: 이번 단위의 파일·hunk만 경로로 지정해 스테이징합니다. `git add -A`는 쓰지 않습니다. 커밋 전에 `git diff --cached --stat`과 본문을 확인합니다. 다른 작업자의 변경, 비밀값, 생성물, `*-plan.md`, `.claude/pr-drafts/`는 넣지 않습니다.
- **제목**: `type(scope): 한국어 변경 요약`. 끝은 `추가`·`차단`·`검증` 같은 명사형으로 하고 `~한다`·`~합니다`는 쓰지 않습니다.
  - type은 [목적 하나](references/change-types.md#change-types)로 고릅니다.
  - scope는 실제 모듈·영역입니다.
  - 예: `feat(schedule): 관리자 작업 상세 조회 추가`, `fix(auth): 다른 발급자의 Access Token 거부`
- **본문**: 단순하지 않은 변경은 문제, 이유, 실제 검증 근거를 적습니다. 대화나 로그를 옮겨 적지 않습니다. 호환성 파괴는 `type(scope)!:`로 표시하고 전환 방법을 적습니다.
- **승인**: amend·rebase·reset·브랜치 삭제·푸시·작업을 잃는 정리는 [Git 승인](references/git-approvals.md#git) 대상입니다. 팀 설정에서 확인(ask) 대상입니다. 보완이 필요하면 새 커밋을 만듭니다.
- **보고**: 커밋 후 해시, 목적, 실제 검사 결과, 남은 전체 검증을 알립니다.

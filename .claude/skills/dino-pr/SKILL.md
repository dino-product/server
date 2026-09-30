---
name: dino-pr
description: PR을 만들거나 PR 초안·본문을 작성할 때("PR 써줘", "PR 올려줘", "PR 준비"), 기능 작업을 마치고 리뷰 요청 전, 또는 변경을 여러 PR로 나눌지 판단할 때 사용합니다. PR 종류를 판별하고 코드에서 시퀀스 다이어그램 등 핵심 다이어그램과 규칙↔테스트 표를 만들어 로컬 초안(.claude/pr-drafts)과 HTML 미리보기를 생성합니다.
---

# PR 초안 작성

사람은 PR 코드 전체가 아니라 **핵심 다이어그램과 구현 내용 표**로 판단합니다. 그래서 다이어그램과 표는 반드시 실제 코드·테스트에서 도출하고 하나하나 대조합니다. 원본 규칙: [종류](references/kinds.md), [단위·stack](references/planning.md), [작성](references/writing.md), [게시 승인](references/publishing-approvals.md).

## 절차

1. **범위 확인**: `python3 .claude/skills/dino-pr/scripts/pr_draft.py context --base <base>`. base는 기본 브랜치(`develop`) 또는 직접 선행 PR 브랜치입니다. 계획 파일(워크트리 루트 `<이슈키>-plan.md`)이 있으면 노션 링크·PR 계획을 가져옵니다.
2. **종류 판별**: [판별 순서](references/kinds.md#decide)대로 하나를 고릅니다. 두 종류가 섞였거나 핵심 다이어그램이 3개를 넘을 것 같으면 초안을 쓰지 말고 [분할안](references/planning.md#unit)을 사용자에게 제안합니다.
3. **핵심 다이어그램**: 진입점(Controller·UseCase·리스너)부터 서비스·포트·도메인 호출을 실제 코드로 따라가며 그립니다. 참여자는 5개 이하의 계층 경계 단위(호출자·서비스·출력 포트·도메인·다른 모듈)로 두고, 메시지는 실제 메서드 이름을 씁니다. 모듈 공통 검사는 `Note` 한 줄로 요약하고 이 기능에만 있는 실패 분기만 `alt`로 오류 코드와 함께 그립니다([작성 기준](references/kinds.md#절별-작성-기준)). 다 그린 뒤 화살표마다 해당 호출이 코드에 있는지 grep으로 확인합니다.
4. **구현 내용 표**: 규칙마다 근거 테스트를 연결합니다(한 클래스면 열 제목에 클래스, 셀에 `#메서드`). 테스트 이름은 실제 파일에서 찾고 없는 테스트를 지어내지 않습니다. 노션 명세와 다르게 구현한 점은 `명세와 차이` 열에 번호만 적고 표 아래에 설명합니다.
5. **나머지 절**: 기능 명세(노션 링크와 절, 링크가 없으면 사용자에게 묻기), 관련 이슈, 기술적 선택(리뷰어 동의가 필요한 것만), 검증(실제 실행한 명령·결과와 통계 한 줄. 실행하지 않은 검사는 미실행으로), 리뷰 포인트. 검사가 아직 돌고 있으면 끝날 때까지 기다린 뒤 초안을 씁니다. `TODO`·`*_PENDING` 같은 자리표시자를 남기지 않습니다.
6. **초안 저장**: `.claude/pr-drafts/<브랜치 이름의 / 를 - 로>.md`. 형식은 `pr_draft.py` docstring의 front matter + 본문입니다. 제목은 `type(scope): 결과 (HM-키)`, 라벨은 제목 type과 같은 `type:*`.
7. **검사와 미리보기**:
   - `python3 .claude/skills/dino-pr/scripts/pr_draft.py check <초안>`: CI의 PR 규약 검사와 같은 판정입니다. 실패하면 고칩니다.
   - `python3 .claude/skills/dino-pr/scripts/pr_draft.py preview <초안>`: `<초안>.html`을 만듭니다. 사용자에게 경로를 알려 GitHub와 같은 모양(mermaid 렌더링 포함)으로 확인하게 합니다.
8. **게시**: 사용자가 PR 생성을 요청했을 때만 합니다. 푸시는 [승인 대상](../../../AGENTS.md#approvals)입니다. `gh pr create --base <base> --title "<title>" --label <label> --body-file <(python3 .claude/skills/dino-pr/scripts/pr_draft.py body <초안>)`. 미완성이면 `--draft`.

## 확인 목록

- [ ] PR 종류와 제목 type이 [허용 조합](references/kinds.md#kinds)인가
- [ ] 다이어그램의 모든 화살표가 코드에 있는 호출인가, 코드의 주요 분기가 빠지지 않았는가
- [ ] 표의 모든 테스트가 실제로 존재하고 이번 검증에서 통과했는가
- [ ] 노션 명세를 복사하지 않고 링크·절만 적었는가
- [ ] `check`가 통과했는가

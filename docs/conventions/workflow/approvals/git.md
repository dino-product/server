<a id="git"></a>
# Git 작업 승인

다음 작업에 [공통 승인](README.md#approvals)을 적용합니다.

| 행위 | 실행 전 요약 |
| --- | --- |
| 푸시 | 원격·브랜치·포함 커밋·원격 이력 변경/강제 옵션 여부 |
| amend·rebase·reset·브랜치 삭제 | 대상 커밋/브랜치·공유 여부·이력/작업 트리 영향 |
| 미커밋·보관한 작업을 잃는 정리·복원 | 경로·stash·worktree·버릴 변경·복구 가능 여부 |

`git clean`, `git restore`, `git checkout -- …`, `git stash drop/clear`, `git worktree remove`는 실제로 미커밋·보관한 작업을 잃는지 확인합니다. 요청에 필요한 소스 삭제·생성물 정리·삭제 없는 조회/미리보기는 기존 자동 범위입니다. `git restore --staged`처럼 작업 파일을 보존하는 스테이징 조정은 다른 작업자의 staged 변경을 보존하며 요청 범위에서 수행합니다. `reset`은 결과가 비슷해도 명시 승인 대상입니다.
